# StoryStep - Initial Report Outline

<!-- AUTHORING NOTES — remove before exporting the final PDF.
This template follows the assignment requirements recorded in ../AGENTS.md.
Replace every [placeholder] and remove instructional text before submission.
Check pagination in the exported PDF: Section 4 must occupy one page; all of
Section 5, including tables and figures, must not exceed 10 pages.
Section 5 covers all 19 supplied rubric criteria (100 marks); the canonical
wording is recorded in ../AGENTS.md. Retain the general application requirements
as well. Do not invent results, contributions, earned scores or consent.
Existing evidence is indexed in verification_checklist.md and verification/.
Verify that any cited evidence applies to the submitted commit and distinguish
physical-device tests, emulator tests, JVM tests and unexecuted checks.
-->

## Draft purpose and length plan

This initial submission establishes the report structure, the intended content of
each subsection, and its expected length. Placeholders are intentional at this
stage. They identify material to develop for the final report, not completed
claims or missing initial-draft deliverables. This plan does not change the final
assignment submission requirements.

Page budgets refer to the final rendered PDF, including tables, figures and
captions. Except for the required one-page contribution breakdown and the
10-page maximum for Section 5, these are team planning targets, not course limits.
Use a readable A4 layout with approximately 11-12 pt body text; adjust prose and
figures rather than shrinking text to meet the budget. Fractional allocations
share pages; do not start every subsection on a new page.

| Report section | Target length | Intended content |
|---|---:|---|
| Cover page | 1 page | Group and member details, version/date; consent only if authorised |
| 1. App overview | 0.5 page | Problem, target users, core journey and delivered scope |
| 2. Demonstration video | 0.5 page | YouTube link, duration/device and concise timestamp map |
| 3. Compile and run instructions | 2 pages | Environment, reproducible setup, phone launch and console screenshot |
| 4. Individual contributions | 1 page (required) | Itemised actual contributions for every member |
| 5. Rubric criteria and justification | 9.5 pages + 0.5 page reserve; maximum 10 | All 19 criteria, with explanations and supporting evidence |
| 6. AI and other assistance acknowledgement | 0.5 page | Tools, concrete uses, affected material and human review |
| 7. References and attributions | 0.5-1 page | Sources, third-party material and tool citations |
| **Final report planning total** | **16-16.5 pages including Section 5 reserve** | No overall report page limit was supplied |

The length plan itself is for draft review and is excluded from the final report
page budget. Remove it and the inline length guidance before final PDF export.

### Section 3 allocation - 2 pages

| Subsection | Target length | Expected output |
|---|---:|---|
| 3.1 Tested environment and prerequisites | 0.5 page | Compact version/dependency and device table |
| 3.2 Build steps | 0.5 page | Ordered setup/build commands and APK location |
| 3.3 Install and run on a phone | 0.25 page | Device connection, permissions and first-run scenario |
| 3.4 Android Studio compilation evidence | 0.75 page | Legible console screenshot with build/version caption |

### Section 5 allocation - 9.5 pages plus 0.5 page reserve

For each criterion, use a concise claim, implementation explanation, evidence
reference and limitation where relevant. The prompts below describe expected
content; they are not prose to retain in the final report. Material criteria
cross-reference Sections 2-4 and the packaged artifacts rather than repeating
them. A single figure can support several criteria through explicit references.

| Subsection | Criterion | Marks | Target pages |
|---|---|---:|---:|
| 5.1 | Coverage map | - | 0.50 |
| 5.2 | Material - Report & Video | 6 | 0.125 |
| 5.3 | Material - Screenshot | 2 | 0.125 |
| 5.4 | Material - Commit log | 4 | 0.125 |
| 5.5 | Material - Itemised contributions | 2 | 0.125 |
| 5.6 | Implementation - Quality | 10 | 0.75 |
| 5.7 | Implementation - Sensors | 10 | 1.25 |
| 5.8 | Implementation - Connectivity | 12 | 1.00 |
| 5.9 | Implementation - Responsiveness | 6 | 0.50 |
| 5.10 | Implementation - Technical depth | 6 | 1.00 |
| 5.11 | User Interface - Appeal | 4 | 0.40 |
| 5.12 | User Interface - Guidelines | 6 | 0.40 |
| 5.13 | User Interface - Flow | 6 | 0.40 |
| 5.14 | User Interface - Language | 4 | 0.30 |
| 5.15 | User Interface - Reactiveness | 6 | 0.50 |
| 5.16 | Innovation - Novelty | 3 | 0.30 |
| 5.17 | Innovation - Surprise | 3 | 0.30 |
| 5.18 | Innovation - Tech Knowledge | 4 | 0.50 |
| 5.19 | Innovation - Cross-Disciplinary | 3 | 0.40 |
| 5.20 | Innovation - Impact | 3 | 0.50 |
| **Content total** | **All 19 criteria and coverage map** | **100** | **9.50** |
| Layout reserve | Figure/caption expansion and page breaks | - | 0.50 |
| **Section maximum** | | | **10.00** |

For initial-draft review, confirm that every subsection has a clear purpose,
that no criterion is omitted, and that these allocations reflect the space needed
for explanation and evidence. Actual results, screenshots, member information
and video links can remain explicit placeholders until the final-report stage.

## Cover page

**Planned final length:** 1 page.

**Subject:** COMP90018 — Mobile Computing Systems Programming\
**Project:** StoryStep\
**Group number:** [Group number]\
**Submission date:** [Date]\
**Submitted commit:** [Full Git commit hash]

| Student name | Student number | University email | GitHub handle |
|---|---|---|---|
| [Name] | [Number] | [Email] | [Handle] |
| [Add one row per student] | [Number] | [Email] | [Handle] |

<!-- OPTIONAL PUBLICITY CONSENT: only if the group explicitly agrees, insert the
following exact sentence as visible text on this cover page. Otherwise omit it.
Consent does not affect grading.
We authorise the University of Melbourne to use material from our submission for publicity.
-->

## 1. App overview

**Planned final length:** 0.5 page.

[Briefly explain the problem, intended users and the delivered app's main user
journey. Describe the submitted implementation and its boundaries. Use README.md
as the scope reference; do not describe planned features as completed.]

## 2. Demonstration video

**Planned final length:** 0.5 page.

**YouTube link:** [Insert accessible YouTube URL]\
**Duration:** [MM:SS — maximum 10 minutes]\
**Demonstration phone and Android version:** [Model and version]

[Show the app functioning and every feature required by the marking rubric.
Check that assessors can open and play the linked video.]

| Video timestamp | Feature demonstrated | Exact rubric criterion | Observable result |
|---|---|---|---|
| [MM:SS–MM:SS] | [Feature] | [ID and title] | [What the viewer can verify] |
| [Repeat for every required feature] | [Feature] | [ID and title] | [Result] |

## 3. Compile and run instructions

**Planned final length:** 2 pages total.

### 3.1 Tested environment and prerequisites

**Planned final length:** 0.5 page(s).

| Item | Version or setup used for the submitted build |
|---|---|
| Operating system | [OS and version] |
| Android Studio | [Version] |
| Android SDK | [Installed SDK platform and tools; confirm against submitted build] |
| Gradle and Android Gradle Plugin | [Versions from the submitted repository] |
| Java toolchain | [Resolved JDK version and any setup needed] |
| Phone | [Model, Android version and available sensors] |
| Network and external services | [Connectivity, endpoints and any required setup] |

[Document any local SDK configuration or credentials needed, without embedding
secrets. State how to obtain any required configuration.]

### 3.2 Build steps

**Planned final length:** 0.5 page(s).

1. Extract the submitted code ZIP to [directory].
2. Open the project root containing `settings.gradle.kts` in Android Studio.
3. [Describe SDK installation, Gradle sync and any additional configuration.]
4. From the project root, build the debug APK:

   ```powershell
   # Windows PowerShell
   .\gradlew.bat assembleDebug
   ```

   ```bash
   # macOS / Linux
   ./gradlew assembleDebug
   ```

5. Find the APK at `app/build/outputs/apk/debug/app-debug.apk`.

[Confirm these steps from a clean extraction of the actual code ZIP. Record any
platform-specific adjustments and expected build output.]

### 3.3 Install and run on a phone

**Planned final length:** 0.25 page(s).

1. [Enable developer options and USB debugging; connect and authorise the phone.]
2. [Select the phone and run the app from Android Studio, or describe APK installation.]
3. [Explain first launch, permissions, location services and network requirements.]
4. [Give a reproducible exploration scenario, including location or data prerequisites.]
5. [Explain expected sensor behaviour and fallbacks if permissions or sensors are unavailable.]

### 3.4 Android Studio compilation evidence

**Planned final length:** 0.75 page(s).

**Screenshot:** [Insert the actual Android Studio Console screenshot here before PDF export.]\
**Build date and submitted commit:** [Date; hash]\
**Build task and result:** [Task; observed result]

[The screenshot must demonstrate successful compilation and be legible in the PDF.
Do not substitute an unverified success statement or an unrelated terminal image.]

## 4. Individual contributions — one page

[Keep this entire section to one page in the final PDF. Itemize actual work by
every student; grading is individual. Add rows as needed for distinct contributions.
Identify shared work accurately and reference supporting commits, PRs or artifacts.]

| Student | Itemized completed contributions | Evidence | Shared work / individual responsibility |
|---|---|---|---|
| [Name] | [Implementation; tests; design; documentation; demonstration] | [Commit / PR / file] | [Specific role] |
| [Repeat for each student] | [Actual deliverables] | [Evidence] | [Specific role] |

## 5. Rubric criteria and justification - maximum 10 pages

**Planned final length:** 9.5 pages of content plus 0.5 page reserve; maximum 10 pages.

[Keep all of Section 5, including the coverage map, within 10 PDF pages. For every
criterion, explain HOW it is fulfilled and WHY the evidence supports the claim.
Reference material elsewhere in this report rather than duplicating screenshots
or contributions. The marks below are available marks, not self-awarded scores.]

### 5.1 Coverage map

**Planned final length:** 0.5 page(s), including any figures.

Category totals: **Material 14**, **Implementation 44**, **User Interface 26**,
**Innovation 16**. Total: **100 marks across 19 criteria**.

| Rubric criterion | Marks | Subsection | Evidence / video timestamp | Limitations |
|---|---:|---|---|---|
| Material - Report & Video | 6 | 5.2 | [Artifact / MM:SS, as applicable] | [Gaps] |
| Material - Screenshot | 2 | 5.3 | [Artifact / MM:SS, as applicable] | [Gaps] |
| Material - Commit log | 4 | 5.4 | [Artifact / MM:SS, as applicable] | [Gaps] |
| Material - Itemised contributions | 2 | 5.5 | [Artifact / MM:SS, as applicable] | [Gaps] |
| Implementation - Quality | 10 | 5.6 | [Artifact / MM:SS, as applicable] | [Gaps] |
| Implementation - Sensors | 10 | 5.7 | [Artifact / MM:SS, as applicable] | [Gaps] |
| Implementation - Connectivity | 12 | 5.8 | [Artifact / MM:SS, as applicable] | [Gaps] |
| Implementation - Responsiveness | 6 | 5.9 | [Artifact / MM:SS, as applicable] | [Gaps] |
| Implementation - Technical depth | 6 | 5.10 | [Artifact / MM:SS, as applicable] | [Gaps] |
| User Interface - Appeal | 4 | 5.11 | [Artifact / MM:SS, as applicable] | [Gaps] |
| User Interface - Guidelines | 6 | 5.12 | [Artifact / MM:SS, as applicable] | [Gaps] |
| User Interface - Flow | 6 | 5.13 | [Artifact / MM:SS, as applicable] | [Gaps] |
| User Interface - Language | 4 | 5.14 | [Artifact / MM:SS, as applicable] | [Gaps] |
| User Interface - Reactiveness | 6 | 5.15 | [Artifact / MM:SS, as applicable] | [Gaps] |
| Innovation - Novelty | 3 | 5.16 | [Artifact / MM:SS, as applicable] | [Gaps] |
| Innovation - Surprise | 3 | 5.17 | [Artifact / MM:SS, as applicable] | [Gaps] |
| Innovation - Tech Knowledge | 4 | 5.18 | [Artifact / MM:SS, as applicable] | [Gaps] |
| Innovation - Cross-Disciplinary | 3 | 5.19 | [Artifact / MM:SS, as applicable] | [Gaps] |
| Innovation - Impact | 3 | 5.20 | [Artifact / MM:SS, as applicable] | [Gaps] |

[Also demonstrate actual phone operation in Sections 2 and 3. Explain substantial
implementation effort using the engineering evidence below. Do not treat a build
or emulator run alone as proof of functioning on a physical phone.]

### 5.2 Material - Report & Video - 6 marks

**Planned final length:** 0.125 page(s), including any figures.

> A report & video demonstrating the application functioning. Does the video show all features? Does it convince that the software works as expected? Does the report cover all grading criteria?

[Reference Section 2 and this coverage map; explain feature coverage and why the recorded results demonstrate expected behaviour.]

- **How and why the criterion is met:** [Specific explanation and justification.]
- **Evidence:** [Artifact, source/test reference, observed result and video timestamp where applicable.]
- **Limitations:** [Partial completion, unexecuted checks or bounds on the claim.]

### 5.3 Material - Screenshot - 2 marks

**Planned final length:** 0.125 page(s), including any figures.

> A screenshot of the Android Studio Console demonstrating that the app compiles.

[Reference the actual compilation screenshot in Section 3.4 and identify its build task and submitted commit.]

- **How and why the criterion is met:** [Specific explanation and justification.]
- **Evidence:** [Artifact, source/test reference, observed result and video timestamp where applicable.]
- **Limitations:** [Partial completion, unexecuted checks or bounds on the claim.]

### 5.4 Material - Commit log - 4 marks

**Planned final length:** 0.125 page(s), including any figures.

> An export of commit logs from GIT/SVN/CVS/RCS

[Identify the submitted commit-log ZIP, the exported log filename, export command and history range. Confirm that the export includes the submitted revision.]

- **How and why the criterion is met:** [Specific explanation and justification.]
- **Evidence:** [Artifact, source/test reference, observed result and video timestamp where applicable.]
- **Limitations:** [Partial completion, unexecuted checks or bounds on the claim.]

### 5.5 Material - Itemised contributions - 2 marks

**Planned final length:** 0.125 page(s), including any figures.

> Itemised contributions by each member (1 page document).

[Reference the one-page Section 4 and confirm that every member has itemised actual contributions.]

- **How and why the criterion is met:** [Specific explanation and justification.]
- **Evidence:** [Artifact, source/test reference, observed result and video timestamp where applicable.]
- **Limitations:** [Partial completion, unexecuted checks or bounds on the claim.]

### 5.6 Implementation - Quality - 10 marks

**Planned final length:** 0.75 page(s), including any figures.

> Is the software code readable, self-explanatory, well-formatted, and consistent?

[Explain naming, formatting, architecture and consistency using representative source references and actual review findings.]

- **How and why the criterion is met:** [Specific explanation and justification.]
- **Evidence:** [Artifact, source/test reference, observed result and video timestamp where applicable.]
- **Limitations:** [Partial completion, unexecuted checks or bounds on the claim.]

### 5.7 Implementation - Sensors - 10 marks

**Planned final length:** 1.25 page(s), including any figures.

> Have multiple sensors been used, demonstrating advanced understanding of sensors?

[For each sensor, describe its purpose, processing, accuracy/noise handling, lifecycle and fallback. Explain how their combined use demonstrates advanced understanding; distinguish physical-device evidence from simulated or untested capabilities.]

- **How and why the criterion is met:** [Specific explanation and justification.]
- **Evidence:** [Artifact, source/test reference, observed result and video timestamp where applicable.]
- **Limitations:** [Partial completion, unexecuted checks or bounds on the claim.]

### 5.8 Implementation - Connectivity - 12 marks

**Planned final length:** 1 page(s), including any figures.

> Does the application make use of data from the Internet/cloud or other sources?

[Describe sources, data flow and user benefit. Provide observed online results and relevant cache, offline and error behaviour.]

- **How and why the criterion is met:** [Specific explanation and justification.]
- **Evidence:** [Artifact, source/test reference, observed result and video timestamp where applicable.]
- **Limitations:** [Partial completion, unexecuted checks or bounds on the claim.]

### 5.9 Implementation - Responsiveness - 6 marks

**Planned final length:** 0.5 page(s), including any figures.

> Does the application run and respond in real-time without substantial lag/delay?

[Describe asynchronous work and measured response times on a named phone under stated conditions. Record lag, delays and untested scenarios honestly.]

- **How and why the criterion is met:** [Specific explanation and justification.]
- **Evidence:** [Artifact, source/test reference, observed result and video timestamp where applicable.]
- **Limitations:** [Partial completion, unexecuted checks or bounds on the claim.]

### 5.10 Implementation - Technical depth - 6 marks

**Planned final length:** 1 page(s), including any figures.

> Does the software make use of advanced algorithms relating to one or more of: sensing, localisation, privacy, communication?

[Explain implemented algorithms in one or more listed areas, their inputs, processing, tradeoffs and validation. Justify algorithmic depth rather than relying on feature counts.]

- **How and why the criterion is met:** [Specific explanation and justification.]
- **Evidence:** [Artifact, source/test reference, observed result and video timestamp where applicable.]
- **Limitations:** [Partial completion, unexecuted checks or bounds on the claim.]

### 5.11 User Interface - Appeal - 4 marks

**Planned final length:** 0.4 page(s), including any figures.

> Overall, is the application visually appealing and pleasant?

[Use representative screenshots and actual feedback to explain typography, colour, spacing and visual consistency.]

- **How and why the criterion is met:** [Specific explanation and justification.]
- **Evidence:** [Artifact, source/test reference, observed result and video timestamp where applicable.]
- **Limitations:** [Partial completion, unexecuted checks or bounds on the claim.]

### 5.12 User Interface - Guidelines - 6 marks

**Planned final length:** 0.4 page(s), including any figures.

> Does it follow the UI guidelines of the operating system?

[Identify and cite applicable Android UI guidelines; map them to specific interface decisions and checks.]

- **How and why the criterion is met:** [Specific explanation and justification.]
- **Evidence:** [Artifact, source/test reference, observed result and video timestamp where applicable.]
- **Limitations:** [Partial completion, unexecuted checks or bounds on the claim.]

### 5.13 User Interface - Flow - 6 marks

**Planned final length:** 0.4 page(s), including any figures.

> Is it clear and easy to access/find/activate the features of the app?

[Show core task paths and feature discoverability, supported by actual task-based usability observations.]

- **How and why the criterion is met:** [Specific explanation and justification.]
- **Evidence:** [Artifact, source/test reference, observed result and video timestamp where applicable.]
- **Limitations:** [Partial completion, unexecuted checks or bounds on the claim.]

### 5.14 User Interface - Language - 4 marks

**Planned final length:** 0.3 page(s), including any figures.

> Are the labels and messages meaningful and appropriate?

[Give examples of labels, permission explanations, empty states and errors, and explain their clarity and appropriateness.]

- **How and why the criterion is met:** [Specific explanation and justification.]
- **Evidence:** [Artifact, source/test reference, observed result and video timestamp where applicable.]
- **Limitations:** [Partial completion, unexecuted checks or bounds on the claim.]

### 5.15 User Interface - Reactiveness - 6 marks

**Planned final length:** 0.5 page(s), including any figures.

> Does the interface dynamically react to sensors or external data?

[Show an input change and resulting UI change, such as heading, movement or weather. Link observed behaviour to video timestamps; distinguish this from execution speed.]

- **How and why the criterion is met:** [Specific explanation and justification.]
- **Evidence:** [Artifact, source/test reference, observed result and video timestamp where applicable.]
- **Limitations:** [Partial completion, unexecuted checks or bounds on the claim.]

### 5.16 Innovation - Novelty - 3 marks

**Planned final length:** 0.3 page(s), including any figures.

> Does the application provide a novel functionality, service, or capability?

[Identify the implemented novel capability and compare it with cited existing approaches; bound originality claims.]

- **How and why the criterion is met:** [Specific explanation and justification.]
- **Evidence:** [Artifact, source/test reference, observed result and video timestamp where applicable.]
- **Limitations:** [Partial completion, unexecuted checks or bounds on the claim.]

### 5.17 Innovation - Surprise - 3 marks

**Planned final length:** 0.3 page(s), including any figures.

> Is the application a surprising or unexpected extension of existing apps?

[Explain the unexpected extension of existing app behaviour and illustrate it with a concrete scenario.]

- **How and why the criterion is met:** [Specific explanation and justification.]
- **Evidence:** [Artifact, source/test reference, observed result and video timestamp where applicable.]
- **Limitations:** [Partial completion, unexecuted checks or bounds on the claim.]

### 5.18 Innovation - Tech Knowledge - 4 marks

**Planned final length:** 0.5 page(s), including any figures.

> How well does the app demonstrate integration of knowledge in the Computing and Information Systems domain?

[Explain how computing and information-systems concepts are integrated, with implementation and design evidence.]

- **How and why the criterion is met:** [Specific explanation and justification.]
- **Evidence:** [Artifact, source/test reference, observed result and video timestamp where applicable.]
- **Limitations:** [Partial completion, unexecuted checks or bounds on the claim.]

### 5.19 Innovation - Cross-Disciplinary - 3 marks

**Planned final length:** 0.4 page(s), including any figures.

> Does the app make use of ideas from other fields/disciplines/domains and skilfully applies them?

[Identify the other discipline, cite the relevant idea and explain how it is applied skilfully in the implementation.]

- **How and why the criterion is met:** [Specific explanation and justification.]
- **Evidence:** [Artifact, source/test reference, observed result and video timestamp where applicable.]
- **Limitations:** [Partial completion, unexecuted checks or bounds on the claim.]

### 5.20 Innovation - Impact - 3 marks

**Planned final length:** 0.5 page(s), including any figures.

> Does the application have the potential to improve people's lives, tasks, or activities?

[Identify intended beneficiaries and practical improvements. Support potential impact with scenarios or actual feedback, distinguishing potential from measured outcomes.]

- **How and why the criterion is met:** [Specific explanation and justification.]
- **Evidence:** [Artifact, source/test reference, observed result and video timestamp where applicable.]
- **Limitations:** [Partial completion, unexecuted checks or bounds on the claim.]

## 6. AI and other assistance acknowledgement

**Planned final length:** 0.5 page.

[Identify every AI or other assistance tool used, including editing and translation
tools where applicable. Give concrete examples of use and acknowledge/cite included
outputs. Describe the team's review accurately; students remain responsible for
the final submission's originality, quality and content.]

| Tool / model / version (if known) | Purpose and concrete example of use | Affected material | Human review, changes and validation | Citation / record |
|---|---|---|---|---|
| OpenAI Codex [model/version if known] | Created and updated the Markdown report template from the supplied assignment instructions and marking rubric | Initial structure of this report, if retained | [Record actual review and edits] | [Date and session reference, if available] |
| [Other tool or additional use] | [Example] | [Code / prose / design / translation] | [Actual checks] | [Reference] |

## 7. References and attributions

**Planned final length:** 0.5-1 page.

[List cited sources, third-party ideas, code, datasets, images, libraries and tool
outputs as appropriate. Connect entries to in-text citations and acknowledge
applicable licences. Do not include credentials or private session links that
assessors cannot access.]

1. [Author/provider, title, date/version, URL and access date where applicable.]

<!-- PACKAGING AND VIVA CHECKLIST — working notes; remove from the final report.

Outer submission: COMP90018 - Assignment - Group #.zip
Suggested internal filenames (only the outer filename is prescribed):
  StoryStep-Report.pdf
  StoryStep-Code.zip
  StoryStep-Commit-Logs.zip

- [ ] Replace # with the actual group number.
- [ ] Include the PDF report and both nested ZIP files.
- [ ] Include an exported Git commit log in the commit-log ZIP.
- [ ] Check that the code ZIP and report identify the same submitted version.
- [ ] Verify build/run instructions from the packaged code.
- [ ] Include every student's details and GitHub handle.
- [ ] Verify the YouTube link, duration <= 10 minutes and full rubric coverage.
- [ ] Include a legible Android Studio Console compilation screenshot.
- [ ] Confirm contributions occupy one page in the PDF.
- [ ] Confirm all 19 rubric criteria are addressed and the full justification section occupies <= 10 pages.
- [ ] Replace all placeholders; remove authoring notes; inspect the exported PDF.
- [ ] Complete AI/tool disclosures, citations and other attributions.
- [ ] Include publicity consent on the cover only if the group explicitly agrees.
- [ ] Each student prepares for the individual in-person software viva.
- [ ] Check the official deadline: up to 72 hours late without penalty;
      subsequent delays incur 5% per day late.
-->
