---
name: verify
description: Build, launch, and drive this Spring Boot EC app end-to-end via curl for runtime verification.
---

# Build & launch

- JAVA_HOME must point at a real JDK (JDK 22/23 both worked; README says JDK 23 is required but JDK 22 compiled and ran fine in practice).
- `export JAVA_HOME="/c/Program Files/Java/jdk-22"` then `./mvnw.cmd -q -DskipTests spring-boot:run` with `run_in_background: true`.
- Fresh checkout has no `ecsite.db` (gitignored) — first boot creates SQLite schema fresh via `ddl-auto=update`, including any new columns/constraints on entities.
- Wait for readiness with a poll loop (`until curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/login | grep -q 200; do sleep 1; done`) rather than a fixed sleep — startup takes ~5s.
- **Gotcha**: launching the same `spring-boot:run` command twice (e.g. once via `nohup ... &` then again via `run_in_background`) leaves the first one bound to port 8080 and the second fails with "Port 8080 was already in use" — `ps aux` in Git Bash may not show the surviving detached java.exe. Check with PowerShell instead: `Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Select ProcessId,CommandLine`.
- Stop with `Stop-Process -Id <pid> -Force` via PowerShell (Git Bash `ps`/`kill` don't reliably see these processes).

# Test accounts (seeded by DataInitializer)

- Admin: `admin@example.com` / `admin123`
- User: `user@example.com` / `user1234`

# Driving it (curl + CSRF)

CSRF protection is on (Spring Security default). Flow per session:
1. `curl -c cookies.txt http://localhost:8080/login -o page.html`
2. Extract token: `grep -o 'name="_csrf" value="[^"]*"' page.html | head -1 | sed 's/.*value="\(.*\)"/\1/'` — **use `head -1`**, pages often embed the same hidden `_csrf` field more than once (e.g. header logout form + page form); without `head -1` the token variable ends up as the value repeated on multiple lines and every POST 403s.
3. POST with `-b cookies.txt -c cookies.txt` (persist + update cookies) and `--data-urlencode` for each field including `_csrf`.
4. Re-fetch the target GET page before every POST that needs a fresh CSRF token/hidden fields (login page, `/checkout`, `/admin/coupons/new`, product page for `/cart/add`, order detail page for `/mypage/orders/{id}/cancel`).

Key endpoints: `/cart/add` (`productId`, `quantity`), `/checkout` (`address`, `paymentMethod`, `couponCode`), `/admin/coupons` POST (`code`, `discountType` enum name e.g. `FIXED_AMOUNT`/`PERCENTAGE`, `discountValue`, `minOrderAmount`, `usageLimit`, `active`), `/mypage/orders/{id}/cancel`.

Business error messages land in `<p class="alert-error">...</p>` on the re-rendered form page (HTTP 200), not as a redirect.
