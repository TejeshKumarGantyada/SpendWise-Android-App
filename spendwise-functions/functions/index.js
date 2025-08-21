require("dotenv").config();

const { google } = require("googleapis");
const {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentDeleted,
} = require("firebase-functions/v2/firestore");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { GoogleGenerativeAI } = require("@google/generative-ai");
const { onUserDeleted } = require("firebase-functions/v1/auth");
const { getFirestore } = require("firebase-admin/firestore");
const { initializeApp } = require("firebase-admin/app");

// Initialize Firebase Admin SDK
initializeApp();

// Environment Variables
const SHEET_ID = process.env.SPENDWISE_SHEET_ID;
const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);

//
// ---------------- Google Sheets Helpers ----------------
//

/**
 * Updates or appends a row in the Google Sheet for a transaction.
 * @param {string} transactionId - The ID of the document.
 * @param {object} data - The transaction data.
 */
async function updateSheet(transactionId, data) {
  const auth = await google.auth.getClient({
    scopes: ["https://www.googleapis.com/auth/spreadsheets"],
  });
  const sheets = google.sheets({ version: "v4", auth });

  const rowData = [
    transactionId,
    data.userId || "",
    new Date(data.date).toISOString(),
    data.type,
    data.category,
    data.amount,
    data.note,
  ];

  const findResponse = await sheets.spreadsheets.values.get({
    spreadsheetId: SHEET_ID,
    range: "A:A",
  });

  const values = findResponse.data.values || [];
  const existingRowIndex = values.findIndex((row) => row[0] === transactionId);

  if (existingRowIndex > -1) {
    // Update existing row
    const targetRow = existingRowIndex + 1;
    await sheets.spreadsheets.values.update({
      spreadsheetId: SHEET_ID,
      range: `A${targetRow}`,
      valueInputOption: "USER_ENTERED",
      resource: { values: [rowData] },
    });
    console.log(`Updated row ${targetRow} for transaction ${transactionId}`);
  } else {
    // Append new row
    await sheets.spreadsheets.values.append({
      spreadsheetId: SHEET_ID,
      range: "A1",
      valueInputOption: "USER_ENTERED",
      resource: { values: [rowData] },
    });
    console.log(`Appended new row for transaction ${transactionId}`);
  }
}

/**
 * Deletes a row from the Google Sheet based on a transaction ID.
 * @param {string} transactionId - The ID of the document to delete.
 */
async function deleteFromSheet(transactionId) {
  const auth = await google.auth.getClient({
    scopes: ["https://www.googleapis.com/auth/spreadsheets"],
  });
  const sheets = google.sheets({ version: "v4", auth });

  const findResponse = await sheets.spreadsheets.values.get({
    spreadsheetId: SHEET_ID,
    range: "A:A",
  });

  const values = findResponse.data.values || [];
  const existingRowIndex = values.findIndex((row) => row[0] === transactionId);

  if (existingRowIndex > -1) {
    await sheets.spreadsheets.batchUpdate({
      spreadsheetId: SHEET_ID,
      resource: {
        requests: [
          {
            deleteDimension: {
              range: {
                sheetId: 0,
                dimension: "ROWS",
                startIndex: existingRowIndex,
                endIndex: existingRowIndex + 1,
              },
            },
          },
        ],
      },
    });

    const rowNum = existingRowIndex + 1;
    console.log(`Deleted row ${rowNum} for transaction ${transactionId}`);
  }
}

//
// ---------------- Firestore Triggers ----------------
//

exports.onTransactionCreated = onDocumentCreated(
  "users/{userId}/transactions/{transactionId}",
  (event) => {
    const { transactionId, userId } = event.params;
    const transactionData = event.data.data();
    transactionData.userId = userId;
    return updateSheet(transactionId, transactionData);
  }
);

exports.onTransactionUpdated = onDocumentUpdated(
  "users/{userId}/transactions/{transactionId}",
  (event) => {
    const { transactionId, userId } = event.params;
    const transactionData = event.data.after.data();
    transactionData.userId = userId;
    return updateSheet(transactionId, transactionData);
  }
);

exports.onTransactionDeleted = onDocumentDeleted(
  "users/{userId}/transactions/{transactionId}",
  (event) => {
    const { transactionId } = event.params;
    return deleteFromSheet(transactionId);
  }
);

//
// ---------------- User Data Cleanup ----------------
//

exports.cleanupUserData = onCall(async (request) => {
  const userId = request.auth?.uid;

  if (!userId) {
    throw new HttpsError("unauthenticated", "User must be authenticated");
  }

  console.log(`Starting data deletion for user: ${userId}`);
  const db = getFirestore();
  const userDocRef = db.collection("users").doc(userId);

  try {
    await db.recursiveDelete(userDocRef);
    console.log(`Successfully deleted all Firestore data for user: ${userId}`);
    return { success: true, message: "User data deleted successfully" };
  } catch (error) {
    console.error(`Error deleting Firestore data for user ${userId}:`, error);
    throw new HttpsError("internal", "Failed to delete user data");
  }
});

//
// ---------------- AI Financial Insight ----------------
//

/**
 * A callable function that analyzes a user's transactions and returns an AI-generated insight.
 */
exports.getFinancialInsight = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "You must be logged in.");
  }

  const accounts = request.data.accounts;
  if (!accounts || !Array.isArray(accounts) || accounts.length === 0) {
    throw new HttpsError("invalid-argument", "Please provide account data.");
  }

  // --- THIS IS THE NEW, MORE POWERFUL PROMPT ---
  const model = genAI.getGenerativeModel({model: "gemini-1.5-flash"});
  const prompt = `
    You are a friendly and encouraging financial assistant named "SpendWise AI".
    Analyze the following JSON data of a user's financial accounts.
    - "Bank", "Cash", and "Savings" are assets.
    - "Credit Card" and "Loan Taken" are liabilities (debts). A negative balance on a credit card is debt. A positive balance on a "Loan Taken" is debt.
    
    Provide one concise, helpful, and actionable insight based on their overall financial health.
    Focus on their net worth, savings, or debt levels. For credit cards, consider their utilization (balance vs. limit).
    Keep the insight to a maximum of 2-3 short sentences.
    Do not use markdown or formatting. Just return the plain text insight.

    Here is the account data:
    ${JSON.stringify(accounts)}
  `;

  try {
    const result = await model.generateContent(prompt);
    const response = await result.response;
    const insightText = response.text();

    console.log(`Generated insight for user ${request.auth.uid}: ${insightText}`);
    
    return {insight: insightText};
  } catch (error) {
    console.error("Error calling Gemini API:", error);
    throw new HttpsError("internal", "Failed to generate insight.");
  }
});
