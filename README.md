# 🧠 Arabic Date & Time Parser (Kotlin)

A lightweight Kotlin-based parser for extracting dates and times from natural Arabic text.  

Supports Egyptian dialect and handling relative expressions, natural phrases, and numeric formats — fully offline.

---

## 💡 Why This Project?

Arabic temporal parsing is underrepresented in open-source NLP tools,  
especially for dialects like Egyptian Arabic.

This project aims to bridge that gap by providing:

- ✅ Fully offline parsing  
- ✅ Dialect-aware understanding  

Additionally, it was motivated by a practical need:  
when working on edge devices with small language models, temporal expressions are often hallucinated or misinterpreted.  
This parser provides a deterministic, lightweight alternative that avoids those issues entirely.

---

## 🧩 Architecture

The parser is split into two main components:

### 1️⃣ DateParser

Handles:

- Relative dates  
- Weekdays  
- Date ranges  
- Recurring events  

### 2️⃣ TimeParser

Handles:

- Clock formats  
- Natural language expressions  
- Relative durations  

---

## ✨ Features

### 📅 Date Parsing

| Type | Examples |
|------|---------|
| Relative expressions | "بعد يومين", "كمان أسبوع", "بعد 3 شهور" |
| Weekdays | "الأحد الجاي", "الخميس القادم" |
| Ranges | "من الأحد إلى الثلاثاء" |
| Recurring events | "كل يوم جمعة" |
| Flexible formats | 10/3, 2025-12-01, 25 ديسمبر |
| Month & week boundaries | "أول الشهر", "منتصف الأسبوع الجاي" |

### ⏰ Time Parsing

| Type | Examples |
|------|---------|
| Numeric time | 10:30, 3:00 مساء |
| Natural expressions | "تمانية ونص", "تسعة وتلت", "11 إلا ربع" |
| Relative time | "بعد ساعة", "كمان 20 دقيقة" |
| Compound expressions | "بعد ساعة ونص", "بعد ساعتين إلا ربع" |
| Recurring intervals | "كل 3 ساعات" |
| Day parts | "العصر", "بالليل", "بعد الضهر" |

---

## 🎯 Use Cases

- Voice assistants (offline-first)  
- Reminder & scheduling apps  
- Chatbots handling Arabic input  
- Edge AI applications  
- NLP preprocessing pipelines  

---

## 📦 Installation

Just clone the repository and include the module in your Kotlin/Android project:

---
## 🤝 Contributing

Contributions are welcome!
Feel free to submit a PR to improve parsing accuracy, add new expressions, or extend dialect support.

---
