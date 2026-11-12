import { fileURLToPath } from 'node:url'
import { mergeConfig, defineConfig, configDefaults } from 'vitest/config'
import viteConfig from './vite.config'

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      exclude: [...configDefaults.exclude, 'e2e/**'],
      root: fileURLToPath(new URL('./', import.meta.url)),
      reporters: ['default', 'junit'],
      outputFile: { junit: 'test-results/unit.xml' },
      coverage: {
        provider: 'v8',
        include: ['src/**/*.{ts,vue}'],
        exclude: ['src/**/*.d.ts', 'src/**/__tests__/**', 'src/**/*.spec.ts'],
        reporter: ['text', 'html', 'lcov', 'json-summary'],
        reportOnFailure: true,
      },
    },
  }),
)
