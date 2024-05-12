import { Languages, Question, QuestionCoordinates, SubmissionType } from "@cs124/questioner-types"
import type { Collection } from "mongodb"
import { Array } from "runtypes"

export type GetQuestionOptions = { version?: string }

export async function getQuestion(
  collection: Collection,
  coordinates: QuestionCoordinates,
  options: GetQuestionOptions = {},
): Promise<Question | undefined> {
  const { language, path, author } = coordinates

  const questions = Array(Question).check(
    await collection
      .find({
        latest: true,
        "published.path": path,
        "published.author": author,
        ...(options.version ? { version: options.version } : { latestVersion: true }),
        latestVersion: true,
      })
      .project({ published: 1, classification: 1, validationResults: 1 })
      .toArray(),
  )
  if (questions.length > 1) {
    throw new Error(`Found duplicate questions for ${path}/${author}`)
  }
  if (questions.length === 0) {
    return undefined
  }
  const question = questions[0]
  return question.published.languages.includes(language) ? question : undefined
}

export async function getLatestQuestionsByTypeAndLanguage(
  collection: Collection,
  type: SubmissionType,
  language: Languages,
): Promise<Question[]> {
  return Array(Question).check(
    (
      await collection
        .find({
          latest: true,
          latestVersion: true,
        })
        .project({ published: 1, classification: 1, validationResults: 1 })
        .toArray()
    )
      .filter((q) => (type === "TESTTESTING" ? q.validationResults?.canTestTest === true : true))
      .filter((q) => q.published.languages.includes(language)),
  )
}

export async function getLatestQuestionsByType(collection: Collection, type: SubmissionType): Promise<Question[]> {
  return Array(Question).check(
    (
      await collection
        .find({
          latest: true,
          latestVersion: true,
        })
        .project({ published: 1, classification: 1, validationResults: 1 })
        .toArray()
    ).filter((q) => (type === "TESTTESTING" ? q.validationResults?.canTestTest === true : true)),
  )
}

export async function getAllLatestQuestions(collection: Collection): Promise<Question[]> {
  return Array(Question).check(
    await collection
      .find({
        latest: true,
        latestVersion: true,
      })
      .project({ published: 1, classification: 1, validationResults: 1 })
      .toArray(),
  )
}
