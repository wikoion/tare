# Third-party notices

## etekcity_esf551_ble

<https://github.com/ronnnnnnnnnnnnn/etekcity_esf551_ble>

Tare's protocol and body-composition code is an independent Kotlin port of this Python
library, which reverse-engineered Etekcity's scales. Tare takes from it:

- the ESF-24 Bluetooth protocol (frame layouts, command sequence, vendor byte, stored
  readings), the ESF-551's weight frames and unit command, and the advertisement formats used
  to recognise both, in `core/src/commonMain/kotlin/tare/protocol/`
- the body-composition formulas and their constants, ported from `body_metrics.py`, in
  `core/src/commonMain/kotlin/tare/body/BodyComposition.kt`
- the captured frames and vendor-app reference values used as test vectors in
  `core/src/commonTest/`

```
MIT License

Copyright (c) 2024 Ron Ben Yosef

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Libraries

Linked as dependencies, or included in this repository (the Gradle wrapper), each under its
own licence. Versions are those in `gradle/libs.versions.toml` and
`gradle/wrapper/gradle-wrapper.properties`.

| Library | Version | Licence |
|---|---|---|
| [Kotlin](https://github.com/JetBrains/kotlin) | 2.4.21 | Apache 2.0 |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | 1.11.0 | Apache 2.0 |
| [kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime) | 0.8.0 | Apache 2.0 |
| [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform) | 1.12.2 (Material 3: 1.12.0-alpha03) | Apache 2.0 |
| [JetBrains AndroidX Lifecycle](https://github.com/JetBrains/compose-multiplatform-core) for Compose Multiplatform (`lifecycle-runtime-compose`) | 2.11.0 | Apache 2.0 |
| [Kable](https://github.com/JuulLabs/kable) | 0.45.0 | Apache 2.0 |
| [SQLDelight](https://github.com/sqldelight/sqldelight) | 2.4.1 | Apache 2.0 |
| [Gradle](https://github.com/gradle/gradle) wrapper: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` | 9.8.2 | Apache 2.0 |

The Apache License 2.0 is at <https://www.apache.org/licenses/LICENSE-2.0>.
