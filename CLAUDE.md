I execute all git commands you are not allowed unless explicitly told to. 
Before writing any code refer to the most avaiable documentation. Code commentting must be concise and developer friendly. At no point should comments be related to the development of the code or use language like smoke test or other AI obvious language. 

Refer to these rules when commenting as well:
https://mitcommlab.mit.edu/broad/commkit/coding-and-comment-style/

Always make sure to ask any clarifying questions. Dont ask soley to fufill the requiement only when there is genuine confusion or need guidance.

Refer to the spec for guidance and understand that it is a living document. Feel free to push back if there is a design choice that chould be better. The goal at the end of the day is to have an EHR experience similar to a training experience on Epic. Seeded data that you can still interact with and show functionality.

review my uncommitted changes and flag anything that looks risky before I commit

This project should be as close to production as possible, in a feasible way.

These are the main points of this project that are in my resume:

•
Architected backend infrastructure for a HIPAA-compliant digital health platform processing FHIR R4 patient records and
integrating with EHR systems
•
Built an append-only audit logging system capturing user ID, action type, resource type, and timestamp on every PHI access
event across all protected routes, modeled on HIPAA audit requirements
•
Engineered a role-based caretaker access system with secure patient data delegation, enforcing HIPAA privacy compliance
across multi-user clinical relationship