SHADOW V1 - GITHUB UPLOAD GUIDE

IMPORTANT:
Two required GitHub files/folders normally start with a dot and may be hidden
by Android file managers:

1. .gitignore
2. .github/workflows/build.yml

DO NOT RENAME THEM. GitHub Actions needs the exact .github/workflows/build.yml path.

EASIEST METHOD:
- Extract this ZIP.
- Open GitHub in your browser.
- Create/open your SHADOW repository.
- Upload the ENTIRE extracted project contents.
- If your file manager hides dot-files, enable "Show hidden files".
- Make sure the .github folder and .gitignore are uploaded too.

After upload:
GitHub -> Actions -> Build SHADOW APK -> Run workflow

Expected important structure:
SHADOW_V1/
  .github/
    workflows/
      build.yml
  .gitignore
  README.md
  build.gradle
  gradle.properties
  settings.gradle
  app/
    build.gradle
    src/
      main/
        AndroidManifest.xml
        java/com/shadow/ai/MainActivity.java
        res/...

The helper file you are reading is only a guide. It is safe to leave in the
repository, but it is NOT required for the Android build.
