<!-- Thank you for contributing! Keep PRs focused — one feature or one fix per PR. -->

## Summary

<!-- What does this PR change, and why? -->

## Checklist

- [ ] `./gradlew assembleDebug` passes (run inside `android/`)
- [ ] All user-facing strings live in `android/app/src/main/res/values/strings.xml` — no hardcoded UI strings
- [ ] For protocol/network changes: tested against a real CodeCockpit desktop with Remote access enabled
- [ ] For release-blocking changes: `./gradlew assembleRelease` passes (R8 / serialization keep rules)
- [ ] Docs updated if needed (README / README.zh-CN / CHANGELOG *Unreleased* section)
