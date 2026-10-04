import { fileURLToPath } from 'node:url'
import { mergeConfig, defineConfig, configDefaults } from 'vitest/config'
import viteConfig from './vite.config'

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      exclude: [...configDefaults.exclude, 'cypress/**'],
      root: fileURLToPath(new URL('./', import.meta.url)),
      coverage: {
        provider: 'v8',
        reportOnFailure: true,
        reporter: ['text', 'html', 'lcov'],
        reportsDirectory: 'coverage',
        include: ['src/**/*.{js,ts,vue}'],
        exclude: [
          'src/**/*.d.ts',
          'src/**/*.spec.{js,ts}',
          'src/**/__tests__/**',
        ],
      },
    },
  }),
)
