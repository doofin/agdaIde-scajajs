import { defineConfig } from '@vscode/test-cli';

export default defineConfig({
  files: 'out/test/**/*.test.js',
  workspaceFolder: process.cwd(),
  mocha: {
    timeout: 60000
  }
});
