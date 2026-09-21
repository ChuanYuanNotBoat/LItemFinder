# Third-Party Notices

LItemFinder's NeoForge distribution includes the following third-party runtime component through
Jar-in-Jar packaging.

## Xerial SQLite JDBC

- Component: `org.xerial:sqlite-jdbc:3.53.4.0`
- Project: https://github.com/xerial/sqlite-jdbc
- License: Apache License 2.0
- Purpose: SQLite JDBC driver and platform-native SQLite libraries

The complete upstream license texts are retained inside the unmodified nested `sqlite-jdbc` JAR at
`META-INF/maven/org.xerial/sqlite-jdbc/LICENSE` and
`META-INF/maven/org.xerial/sqlite-jdbc/LICENSE.zentus`. SQLite itself is dedicated to the public
domain as described by the upstream SQLite project.

NeoForge and Minecraft are required platform dependencies and are not redistributed inside the
LItemFinder JAR.
