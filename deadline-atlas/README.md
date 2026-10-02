# Deadline Atlas

Competition deadline tracker hosted inside the existing `pouch-studio` GitHub Pages site.

Live path: https://codejedix.github.io/pouch-studio/deadline-atlas/

Features:
- Visual 30 / 60 / 90 day competition roadmap
- Monthly calendar and focus queue
- Deadline risk radar and progress cards
- Supabase PostgreSQL persistence with passwordless email sign-in
- Per-user Row Level Security (RLS)
- One-time migration of existing browser data after the first sign-in
- Smart import for PDF, DOCX, TXT, PNG, JPG and WEBP
- PDF text extraction with OCR fallback for scanned pages
- Image OCR with Tesseract.js
- Automatic date and milestone extraction
- Imported competitions are marked **Needs review**
- JSON backup and ICS calendar export

Seeded from the handwritten plan:
- INNOVA — proposal submission — 11 Oct 2026
- ModelX — proposal submission — 13 Oct 2026
- ModelX — model development target — 26 Oct 2026
- Cybots — 18 Oct 2026

Unclear dates were intentionally not guessed.

## Database

The live app uses the dedicated **Deadline Atlas** Supabase project. The public
browser client contains only the browser-safe anonymous API key; privileged
service-role credentials are never shipped to the page.

The reproducible database definition is in `supabase-schema.sql`. It creates the
`competitions` table, a server-side first-run marker, explicit Data API grants
for authenticated users, and owner-only Row Level Security policies.
