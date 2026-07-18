import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
    // 백엔드는 별도 패키지다(server/tsconfig.json). 브라우저 전제가 깔린
    // next 규칙을 노드 코드에 적용하면 맞지도 않는 경고만 나온다.
    "server/**",
  ]),
]);

export default eslintConfig;
