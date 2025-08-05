const js = require("@eslint/js")
const tsEslint = require("@typescript-eslint/eslint-plugin")
const tsParser = require("@typescript-eslint/parser")
const prettier = require("eslint-config-prettier")

module.exports = [
  js.configs.recommended,
  {
    files: ["**/*.ts", "**/*.tsx"],
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        ecmaVersion: 2018,
        sourceType: "module",
      },
    },
    plugins: {
      "@typescript-eslint": tsEslint,
    },
    rules: {
      ...tsEslint.configs.recommended.rules,
    },
  },
  prettier,
  {
    ignores: ["dist/**", "node_modules/**"],
  },
]
