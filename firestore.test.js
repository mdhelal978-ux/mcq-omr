const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const TEACHER_UID = "teacher_uid_001";
const STUDENT_UID = "student_uid_002";
const OTHER_STUDENT_UID = "other_student_003";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validExamPayload(overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    examCode: "PHY101",
    teacherId: TEACHER_UID,
    teacherName: "Rahim Sir",
    title: "Physics Chapter 1 Quiz",
    subject: "Physics",
    totalQuestions: 5,
    optionStyle: "ABCD",
    marksPerCorrect: 1.0,
    negativeMark: 0.25,
    answerKey: ["A", "B", "C", "D", "A"],
    status: "active",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validExamCodePayload(examId = "exam_1", overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    examId,
    teacherId: TEACHER_UID,
    title: "Physics Chapter 1 Quiz",
    subject: "Physics",
    status: "active",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validSubmissionPayload(overrides = {}) {
  return {
    examId: "exam_1",
    examCode: "PHY101",
    examTitle: "Physics Chapter 1 Quiz",
    subject: "Physics",
    teacherId: TEACHER_UID,
    submittedByUid: STUDENT_UID,
    scannedByRole: "student",
    studentName: "Ayesha Rahman",
    studentRoll: "2026-14",
    optionStyle: "ABCD",
    detectedAnswers: ["A", "B", "D", "D", "NONE"],
    correctAnswers: ["A", "B", "C", "D", "A"],
    correctCount: 3,
    wrongCount: 1,
    unansweredCount: 1,
    score: 2.75,
    maxScore: 5.0,
    percentage: 55.0,
    aiConfidence: "high",
    submittedAt: new Date(Date.now() - 1000),
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write exams, codes, or submissions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("exams").where("teacherId", "==", TEACHER_UID).get());
  await assertFails(unauthDb.collection("examCodes").doc("PHY101").get());
  await assertFails(unauthDb.collection("submissions").where("submittedByUid", "==", STUDENT_UID).get());
  await assertFails(unauthDb.collection("exams").doc("exam_1").set(validExamPayload()));
});

test("Teacher can atomically create exam + examCode, update exam, and list own exams", async () => {
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const batch = teacherDb.batch();
  batch.set(teacherDb.collection("exams").doc("exam_1"), validExamPayload());
  batch.set(teacherDb.collection("examCodes").doc("PHY101"), validExamCodePayload("exam_1"));
  await assertSucceeds(batch.commit());

  await assertSucceeds(teacherDb.collection("exams").where("teacherId", "==", TEACHER_UID).get());

  // Partial update allowed fields
  await assertSucceeds(
    teacherDb.collection("exams").doc("exam_1").update({
      status: "closed",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field & immutable createdAt overwrite
  await assertFails(
    teacherDb.collection("exams").doc("exam_1").update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(
    teacherDb.collection("exams").doc("exam_1").update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject unfiltered list on exams and any list on examCodes
  await assertFails(teacherDb.collection("exams").get());
  await assertFails(teacherDb.collection("examCodes").get());
});

test("Orphan examCode without matching exam is rejected", async () => {
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  await assertFails(
    teacherDb.collection("examCodes").doc("FAKE99").set(validExamCodePayload("missing_exam"))
  );
});

test("Student can lookup active exam by code, submit scanned sheet, and read own submission", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("exams").doc("exam_1").set(validExamPayload());
    await db.collection("examCodes").doc("PHY101").set(validExamCodePayload("exam_1"));
  });

  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const codeSnap = await assertSucceeds(studentDb.collection("examCodes").doc("PHY101").get());
  const examId = codeSnap.data().examId;
  await assertSucceeds(studentDb.collection("exams").doc(examId).get());

  // Submit with optional aiNotes omitted
  await assertSucceeds(
    studentDb.collection("submissions").doc("sub_1").set(validSubmissionPayload())
  );
  // Submit with optional aiNotes present
  await assertSucceeds(
    studentDb.collection("submissions").doc("sub_2").set(
      validSubmissionPayload({ aiNotes: "Question 5 bubble was faint." })
    )
  );

  // Student can list own submissions, teacher can list exam submissions
  await assertSucceeds(
    studentDb.collection("submissions").where("submittedByUid", "==", STUDENT_UID).get()
  );
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  await assertSucceeds(
    teacherDb.collection("submissions")
      .where("teacherId", "==", TEACHER_UID)
      .where("examId", "==", "exam_1")
      .get()
  );
});

test("Cross-user isolation: another student cannot read, list, or spoof submissions", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("exams").doc("exam_1").set(validExamPayload());
    await db.collection("submissions").doc("sub_1").set(validSubmissionPayload());
  });

  const otherDb = testEnv.authenticatedContext(OTHER_STUDENT_UID).firestore();
  await assertFails(otherDb.collection("submissions").doc("sub_1").get());
  await assertFails(otherDb.collection("submissions").get());
  await assertFails(otherDb.collection("submissions").where("submittedByUid", "==", STUDENT_UID).get());
  await assertFails(otherDb.collection("exams").doc("exam_1").update({ title: "Hacked" }));

  // Reject spoofing submittedByUid or @DocumentId `id` field
  await assertFails(
    otherDb.collection("submissions").doc("sub_2").set(
      validSubmissionPayload({ submittedByUid: STUDENT_UID })
    )
  );
  await assertFails(
    otherDb.collection("submissions").doc("sub_3").set(
      validSubmissionPayload({ id: "sub_3", submittedByUid: OTHER_STUDENT_UID })
    )
  );
});

test("Submissions are immutable on update and reject future timestamps", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("exams").doc("exam_1").set(validExamPayload());
    await db.collection("submissions").doc("sub_1").set(validSubmissionPayload());
  });

  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  await assertFails(studentDb.collection("submissions").doc("sub_1").update({ score: 5.0 }));
  await assertFails(
    studentDb.collection("submissions").doc("sub_future").set(
      validSubmissionPayload({ submittedAt: new Date(Date.now() + 60000) })
    )
  );
});
