# Third-party notices

## Token Monitor desktop assets

Tool-logo vector paths are imported from [Token Monitor v0.61.0](https://github.com/Javis603/token-monitor/tree/v0.61.0/assets/icons).
The original source is identified in the drawable comments. The Token Monitor
app mark and project relationship are credited to that upstream project.
The fx mark is adapted from [Token Monitor v0.65.0](https://github.com/Javis603/token-monitor/blob/v0.65.0/assets/icons/fx.svg),
which traces the official vercel-labs fx icon.

Upstream's MIT notice is reproduced below. Service names and logos remain the
marks of their respective owners; their display identifies data sources and
does not imply endorsement or transfer trademark rights.

```text
MIT License

Copyright (c) 2026 Javis

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

## JetBrains Mono

The widget pages bundle a Latin subset of JetBrains Mono (Regular and Bold) as
`app/src/main/res/font/jetbrains_mono_*.ttf`, produced by
`tools/subset-fonts.mjs`. Copyright 2020 The JetBrains Mono Project Authors
(https://github.com/JetBrains/JetBrainsMono). Licensed under the SIL Open Font
License, Version 1.1, reproduced below.

```text
SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007

PREAMBLE
The goals of the Open Font License (OFL) are to stimulate worldwide
development of collaborative font projects, to support the font creation
efforts of academic and linguistic communities, and to provide a free and
open framework in which fonts may be shared and improved in partnership
with others.

The OFL allows the licensed fonts to be used, studied, modified and
redistributed freely as long as they are not sold by themselves. The
fonts, including any derivative works, can be bundled, embedded,
redistributed and/or sold with any software provided that any reserved
names are not used by derivative works. The fonts and derivatives,
however, cannot be released under any other type of license. The
requirement for fonts to remain under this license does not apply
to any document created using the fonts or their derivatives.

DEFINITIONS
"Font Software" refers to the set of files released by the Copyright
Holder(s) under this license and clearly marked as such. This may
include source files, build scripts and documentation.

"Reserved Font Name" refers to any names specified as such after the
copyright statement(s).

"Original Version" refers to the collection of Font Software components as
distributed by the Copyright Holder(s).

"Modified Version" refers to any derivative made by adding to, deleting,
or substituting -- in part or in whole -- any of the components of the
Original Version, by changing formats or by porting the Font Software to a
new environment.

"Author" refers to any designer, engineer, programmer, technical
writer or other person who contributed to the Font Software.

PERMISSION & CONDITIONS
Permission is hereby granted, free of charge, to any person obtaining
a copy of the Font Software, to use, study, copy, merge, embed, modify,
redistribute, and sell modified and unmodified copies of the Font
Software, subject to the following conditions:

1) Neither the Font Software nor any of its individual components,
in Original or Modified Versions, may be sold by itself.

2) Original or Modified Versions of the Font Software may be bundled,
redistributed and/or sold with any software, provided that each copy
contains the above copyright notice and this license. These can be
included either as stand-alone text files, human-readable headers or
in the appropriate machine-readable metadata fields within text or
binary files as long as those fields can be easily viewed by the user.

3) No Modified Version of the Font Software may use the Reserved Font
Name(s) unless explicit written permission is granted by the corresponding
Copyright Holder. This restriction only applies to the primary font name as
presented to the users.

4) The name(s) of the Copyright Holder(s) or the Author(s) of the Font
Software shall not be used to promote, endorse or advertise any
Modified Version, except to acknowledge the contribution(s) of the
Copyright Holder(s) and the Author(s) or with their explicit written
permission.

5) The Font Software, modified or unmodified, in part or in whole,
must be distributed entirely under this license, and must not be
distributed under any other license. The requirement for fonts to
remain under this license does not apply to any document created
using the Font Software.

TERMINATION
This license becomes null and void if any of the above conditions are
not met.

DISCLAIMER
THE FONT SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO ANY WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
OF COPYRIGHT, PATENT, TRADEMARK, OR OTHER RIGHT. IN NO EVENT SHALL THE
COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
INCLUDING ANY GENERAL, SPECIAL, INDIRECT, INCIDENTAL, OR CONSEQUENTIAL
DAMAGES, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF THE USE OR INABILITY TO USE THE FONT SOFTWARE OR FROM
OTHER DEALINGS IN THE FONT SOFTWARE.
```

## Libraries

AndroidX/Jetpack Compose, Kotlin and kotlinx.serialization are used under their
respective open-source licenses. Dependency versions are declared in
`gradle/libs.versions.toml` and `app/build.gradle.kts`; the project does not
relicense those dependencies. Android/Material icons retain their upstream
attribution and licenses.

The optional screenshot-board renderer uses sharp (Apache-2.0) and its bundled
image-processing dependencies. The font subsetter uses subset-font and fontverter
(BSD-3-Clause) plus harfbuzzjs (MIT) and their bundled dependencies. These are
documentation and build tools, not part of the APK.

## Hermes Agent icon

The silhouette in `upstream_logo_hermes.png` is adapted from
[NousResearch/hermes-agent assets/icon-master.svg](https://github.com/NousResearch/hermes-agent/blob/c2b69de66d847318f05356c666f21e046d2e9468/assets/icon-master.svg).
The official silhouette is rasterized at 512×512 with its tile background omitted,
keeping the original path geometry and transparency for theme tinting. This avoids
Android's vector string-size limit. Hermes Agent remains a Nous Research
mark and is used only to identify the data source.

```text
MIT License

Copyright (c) 2025 Nous Research

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

DeepSeek Harness uses the existing DeepSeek whale mark imported from Token Monitor
desktop; the product identity is verified against
[DeepSeek's official Harness repository](https://github.com/deepseek-ai/deepseek-harness).
The Linux/NAS server silhouette is original to this client.

## Device icons (cf.4)

Six user-selected SVGs were retrieved on 2026-10-04 from the following collections
and converted locally to Android VectorDrawable resources. Original path geometry,
colors and linear gradients are retained; Apple is centered in a square viewport
to preserve its aspect ratio. There are no runtime CDN requests. The resource
suffix `dark` means a dark-colored icon for a light background; `light` means a
light-colored icon for a dark background. The default UGREEN icon retains its
original grayscale body and green indicator lights.

| Local resource | Source SVG | Source SHA-256 |
| --- | --- | --- |
| `device_ugreen_dark.xml` | [ugreen-nas.svg](https://cdn.jsdelivr.net/gh/selfhst/icons/svg/ugreen-nas.svg) | `756e4460b4484badb27f7eb9df61783f58c31def727c6e67aedbeed287721496` |
| `device_ugreen_light.xml` | [ugreen-nas-light.svg](https://cdn.jsdelivr.net/gh/selfhst/icons/svg/ugreen-nas-light.svg) | `8bc601c30c2df075a320e7cf7ac368ef05bcfc397d921509c8bd8b3ab890334e` |
| `device_windows_dark.xml` | [microsoft-windows-dark.svg](https://cdn.jsdelivr.net/gh/selfhst/icons/svg/microsoft-windows-dark.svg) | `73761fc12b610116c3d08947a39090a7c5eae18cc9cb5f20f5b82f43d2e75cfe` |
| `device_windows_light.xml` | [microsoft-windows-light.svg](https://cdn.jsdelivr.net/gh/selfhst/icons/svg/microsoft-windows-light.svg) | `c45d823d37fa932f30a0215413a131d69fb1ab8f756d0fac5a00cdd8fb09279f` |
| `device_apple_dark.xml` | [apple.svg](https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/svg/apple.svg) | `72ab034548d45f1bfa327f97b9fb96f7931362a8296289b1512bbe44512df235` |
| `device_apple_light.xml` | [apple-light.svg](https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/svg/apple-light.svg) | `e3f4be3cbefb8ce59f5502900fe70fcb9141d27129297ce725c5764634ee491a` |

UGREEN NAS and Windows are credited to the [selfh.st Icons collection](https://github.com/selfhst/icons).
Its published [license](https://github.com/selfhst/icons/blob/main/LICENSE) is
[Creative Commons Attribution 4.0 International](https://creativecommons.org/licenses/by/4.0/).
The technical changes are the SVG-to-VectorDrawable conversion described above.
UGREEN and Microsoft names and marks remain the property of their respective
owners; the collection license does not grant trademark rights or imply endorsement.

Apple resources are credited to [Homarr Labs Dashboard Icons](https://github.com/homarr-labs/dashboard-icons).
The repository publishes an [Apache-2.0 license](https://github.com/homarr-labs/dashboard-icons/blob/main/LICENSE),
reproduced below. Its [legal disclaimer](https://github.com/homarr-labs/dashboard-icons#legal)
retains third-party ownership of product names and trademarks. This attribution
is not a claim that all brand marks indexed by Dashboard Icons are Apache-licensed,
and no Apple trademark rights or endorsement are granted.

```text
                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS

   APPENDIX: How to apply the Apache License to your work.

      To apply the Apache License to your work, attach the following
      boilerplate notice, with the fields enclosed by brackets "[]"
      replaced with your own identifying information. (Don't include
      the brackets!)  The text should be enclosed in the appropriate
      comment syntax for the file format. We also recommend that a
      file or class name and description of purpose be included on the
      same "printed page" as the copyright notice for easier
      identification within third-party archives.

   Copyright (c) 2024 Bjorn Lammers, Meier Lukas, Thomas Camlong and Homarr Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

```

## cf.6 confirmed monochrome icons — 2026-10-04

OpenAI: Homarr Labs Dashboard Icons `svg/openai.svg` and `svg/openai-light.svg` (Apache-2.0 repository license above; brand trademark terms remain applicable).
DeepSeek: selfh.st `svg/deepseek-dark.svg` and `svg/deepseek-light.svg`.
Hermes Agent: selfh.st `svg/hermes-agent-dark.svg` and `svg/hermes-agent-light.svg`.
UGREEN NAS: selfh.st `svg/ugreen-nas-dark.svg`; white variant retained from cf.4.
Source roots: https://github.com/homarr-labs/dashboard-icons and https://github.com/selfhst/icons . selfh.st resources are CC BY 4.0, attribution and license linked above.

SVG path geometry preserved; fills normalized to confirmed black/white. The OpenAI white SVG contains an invalid nine-digit path fill; normalized to its root white fill. These are local Android vector conversions.

| Resource | Source SVG SHA-256 |
| --- | --- |
| brand_deepseek_dark | 70be459e307f5b55f22bf03c249838d78e16e917b63240ccbed0e0b1065668bf |
| brand_deepseek_light | bd90dfef73c922d632334438f8020ef43eb39f4240b65bc55d28ac50415fac0e |
| brand_hermes_dark | 6fb2bad073b382c5e8023564608908dece63079314835777d4d278b7fbb936ef |
| brand_hermes_light | c703a243737d7a69c2f950fd76864efa1b89282996444abbb01b323e9a952ae1 |
| brand_openai_dark | 8a6030406f34ee761c3d60dd95cc49af8c3ca1c9f45b61a3d8648d60c91cba62 |
| brand_openai_light | 5c94498ddd61f3cde18aee94d2d1a8a593329253bed24c3e84ec419890553f09 |
| device_ugreen_dark | 8a733f2e38bc374cb76953a310c9e675b2f28825b6361dbf0c1f214e309931de |
