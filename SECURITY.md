# Security Policy

This project handles subsistence-farming household operating workflows. Treat
vulnerabilities as potentially high impact even when the demo data is
synthetic — this domain's failure modes include household food-security risk
alongside standard agricultural physical-safety hazards.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real farmer, plot or operator data exposure
- authorization bypass
- Subsistence Farm Governor bypass
- audit-ledger tampering
- over-disclosure in reports or exports
- unsafe robot action dispatch
- any path that lets a proposal reach a planting/harvest-timing agronomic
  decision, or an override of the farmer's own crop judgment

## Reporting

Use GitHub private vulnerability reporting when available for the repository.
If that is unavailable, contact the repository maintainers through the
cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on farmer/plot data, policy enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real farmer/plot/operator data outside this repository.
- Run policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
