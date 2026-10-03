# AGENTS.md

Project engineering guidance lives in **[`CLAUDE.md`](CLAUDE.md)**.
Assessment and submission requirements are recorded below.

Read it before making any change. It covers the confirmed scope and the explicit
out-of-scope list, the build commands, the package root, the AGP 9 build-DSL forms
that must not be "fixed" back to older syntax, the fixed technology constraints,
the layered architecture and its package map, and the branching and secrets conventions.

Engineering instructions are referenced rather than copied to avoid divergent
guidance. The assessment requirements below do not expand the confirmed project
scope in `CLAUDE.md` and `README.md`.

## Assessment requirements

Source: assignment instructions and marking rubric supplied by the user. The
rubric below totals 100 marks. Do not claim that criteria have been met without
supporting evidence.

Every group must build a mobile app that:

- Runs on a phone.
- Uses multiple sensors.
- Accesses information from the Internet or cloud.
- Is usable.
- Is innovative.
- Requires substantial implementation effort (not a trivial weekend project).

The assignment gives illustrative examples: a game with sensory input and a game
server; a smart-city utility combining public APIs, such as public transport, with
GPS; or a social app using nearby friends, shared context (such as silent mode),
location, and nearby points of interest. These examples are not required features.

## Marking rubric - 100 marks

The wording and marks below are supplied assessment criteria, not earned scores.

| Category | Criterion | Marks | Assessment description |
|---|---|---:|---|
| Material | Report & Video | 6 | A report & video demonstrating the application functioning. Does the video show all features? Does it convince that the software works as expected? Does the report cover all grading criteria? |
| Material | Screenshot | 2 | A screenshot of the Android Studio Console demonstrating that the app compiles. |
| Material | Commit log | 4 | An export of commit logs from GIT/SVN/CVS/RCS |
| Material | Itemised contributions | 2 | Itemised contributions by each member (1 page document). |
| Implementation | Quality | 10 | Is the software code readable, self-explanatory, well-formatted, and consistent? |
| Implementation | Sensors | 10 | Have multiple sensors been used, demonstrating advanced understanding of sensors? |
| Implementation | Connectivity | 12 | Does the application make use of data from the Internet/cloud or other sources? |
| Implementation | Responsiveness | 6 | Does the application run and respond in real-time without substantial lag/delay? |
| Implementation | Technical depth | 6 | Does the software make use of advanced algorithms relating to one or more of: sensing, localisation, privacy, communication? |
| User Interface | Appeal | 4 | Overall, is the application visually appealing and pleasant? |
| User Interface | Guidelines | 6 | Does it follow the UI guidelines of the operating system? |
| User Interface | Flow | 6 | Is it clear and easy to access/find/activate the features of the app? |
| User Interface | Language | 4 | Are the labels and messages meaningful and appropriate? |
| User Interface | Reactiveness | 6 | Does the interface dynamically react to sensors or external data? |
| Innovation | Novelty | 3 | Does the application provide a novel functionality, service, or capability? |
| Innovation | Surprise | 3 | Is the application a surprising or unexpected extension of existing apps? |
| Innovation | Tech Knowledge | 4 | How well does the app demonstrate integration of knowledge in the Computing and Information Systems domain? |
| Innovation | Cross-Disciplinary | 3 | Does the app make use of ideas from other fields/disciplines/domains and skilfully applies them? |
| Innovation | Impact | 3 | Does the application have the potential to improve people's lives, tasks, or activities? |

Category totals: **Material 14**, **Implementation 44**, **User Interface 26**,
and **Innovation 16**; overall **100 marks**.

Use all 19 criteria to organise report justification and demonstration evidence.
Implementation responsiveness concerns runtime lag; UI reactiveness concerns
dynamic responses to sensor or external data. Address both explicitly. Retain
the general application and submission requirements above and below.

## Submission checklist

Submit a single ZIP named `COMP90018 - Assignment - Group #.zip`, replacing `#`
with the group number. It must contain:

1. A PDF report containing:
   - The group number and every student's name, student number, email, and GitHub
     handle.
   - A link to a YouTube demonstration video of up to 10 minutes showing the app
     functioning and showcasing each feature in the marking rubric.
   - Instructions for compiling and running the project.
   - A screenshot of the Android Studio Console demonstrating successful
     compilation.
   - A one-page, itemized breakdown of each student's contributions. Scores are
     calculated individually.
   - A section of up to 10 pages explaining how every rubric criterion is met,
     with justification of how and why each criterion is fulfilled.
2. A ZIP containing the code.
3. A ZIP containing an export of version-control commit logs (GIT/SVN/CVS/RCS;
   this project uses Git).

Each student must also attend an individual, in-person viva and answer questions
about the submitted software.

Submission is allowed up to 3 days (72 hours) late without penalty. Subsequent
delays incur a 5% penalty per day late.

## Optional publicity consent

The University may promote exceptional student work on its website or other
platforms solely for publicity. Consent is optional and does not affect grading.
Only if the group chooses to consent, include this exact statement on the report's
cover page:

> We authorise the University of Melbourne to use material from our submission for publicity.

Do not infer consent or add this statement automatically.

## AI use, attribution, and academic integrity

AI may be used and blended into the final product for this assessment. The report
must identify the tools used and acknowledge examples of how they were used.
Keep enough records of AI assistance to support an accurate disclosure.

All submitted work must be the students' own, original work. Ideas or words from
other people and outputs from AI or other software tools must be appropriately
acknowledged and cited. This includes editing and translation assistance, such as
Grammarly, where permitted. Failure to acknowledge or cite such assistance will
be reported as potential academic misconduct and may attract appropriate penalties.

Submissions must follow the subject coordinator's authorised assessment task.
Students remain responsible for the quality and content of the final submission,
including any AI-assisted material.
