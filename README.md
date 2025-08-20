# SpendWise - A Modern Android Expense Tracker 🦉

SpendWise is a feature-rich, offline-first expense tracking application for Android, built with a modern, industry-standard technology stack. It provides users with powerful tools to manage their finances, from simple transaction logging to AI-powered insights and professional-grade analytics.

The entire application, from the initial concept to the final UI/UX polish, was built collaboratively with a large language model, showcasing a modern AI-assisted development workflow.

---

## 📸 Screenshots

*(Action Required: Replace these placeholders with actual screenshots of your app. You can drag and drop them into the GitHub editor.)*

* **Home Screen**
* **Charts Screen**
* **Add Transaction**
* **Profile Screen**

\[Screenshot of the redesigned Home Screen]
\[Screenshot of the Charts Screen]
\[Screenshot of the Add Transaction Screen with OCR]
\[Screenshot of the enhanced Profile Screen]

---

## ✨ Key Features

This app was built in a phased approach, resulting in a comprehensive feature set:

### Core Features

* **Offline-First Functionality**: Full offline support using a local Room database.
* **Secure Cloud Sync**: Real-time, two-way data synchronization with Firestore.
* **User Authentication**: Polished sign-in/sign-up flow with email/password, profile picture uploads, and user profile management via Firebase Authentication.

### Smart Financial Tools

* **Budgeting System**: Set and track monthly budgets per category with visual progress bars.
* **Recurring Transactions**: Automate regular income and expenses with a daily background worker.
* **Search & Filtering**: Advanced search bar and filters for date ranges and categories.
* **AI-Powered Insights**: "Smart Insight" card powered by Google Gemini API for personalized advice.
* **OCR Receipt Scanning**: Scan receipts using Google ML Kit for automatic total and date extraction.

### Advanced Analytics

* **In-App Charts**: Dynamic Pie, Bar, and Line charts built with Vico.
* **Tableau Integration**: Automated data export to Google Sheets via Firebase Functions, enabling connection to Tableau with row-level security.

### Polished UI/UX

* Custom professional theme with dark mode.
* Dashboard-style home screen.
* Smooth transitions and micro-interactions.
* Customizable categories and currency settings.

---

## 🛠️ Tech Stack & Architecture

* **Platform**: 100% Kotlin, Android Native
* **UI**: Jetpack Compose
* **Architecture**: MVVM (Model-View-ViewModel)
* **Dependency Injection**: Hilt
* **Local Database**: Room
* **Background Processing**: WorkManager

### Backend & Cloud

* **Firebase Authentication**: User management
* **Firestore**: Real-time cloud database
* **Firebase Storage**: Profile picture uploads
* **Firebase Functions (Node.js)**: AI insights, data deletion, and Google Sheets integration

### AI & ML

* **Google Gemini API**: For financial insights
* **Google ML Kit**: For on-device OCR

### Other Libraries

* **Charting**: Vico
* **Image Loading**: Coil
* **Analytics**: Tableau (via Google Sheets)

---

## 🚀 Setup & Installation

To run this project, you will need **Android Studio**, **Node.js**, and the **Firebase CLI**.

### 1. Firebase Project Setup

* Create a new project in the [Firebase Console](https://console.firebase.google.com/).
* Enable Authentication (Email/Password provider), Firestore, Storage, and Functions.
* Upgrade to the **Blaze (pay-as-you-go) plan** for Functions.
* Download the `google-services.json` file and place it in the `app/` directory.

### 2. Backend (Firebase Functions)

* Navigate to `spendwise-functions/functions`.
* Run `npm install`.
* Create a `.env` file in this directory.
* Add secrets:

  ```env
  SPENDWISE_SHEET_ID="YOUR_GOOGLE_SHEET_ID"
  GEMINI_API_KEY="YOUR_GEMINI_API_KEY"
  ```
* Setup a Service Account and share your Google Sheet with it.
* Deploy with:

  ```bash
  firebase deploy --only functions
  ```

### 3. Frontend (Android App)

* Open the project in **Android Studio**.
* Sync Gradle dependencies.
* Run the app on an emulator or physical device.

---

This project was developed as a comprehensive demonstration of modern Android development, from core functionality to advanced cloud and AI integrations.
