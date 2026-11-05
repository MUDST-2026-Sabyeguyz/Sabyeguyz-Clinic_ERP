import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('appointment time drawer layout', () => {
  it.each(['appointments.css', 'manager-appointments.css'])(
    '%s keeps the confirmation footer reachable within the viewport',
    (filename) => {
      const styles = readFileSync(
        resolve(process.cwd(), `src/features/appointments/styles/${filename}`),
        'utf8',
      )
      const drawer = styles.match(/\.appointment-time-drawer \{([\s\S]*?)\n\}/)?.[1] ?? ''
      const content =
        styles.match(/\.appointment-time-drawer-content \{([\s\S]*?)\n\}/)?.[1] ?? ''
      const footer =
        styles.match(/\.appointment-time-drawer footer \{([\s\S]*?)\n\}/)?.[1] ?? ''

      expect(drawer).toContain('height: 100dvh')
      expect(drawer).toContain('box-sizing: border-box')
      expect(drawer).toContain('overflow: hidden')
      expect(content).toContain('overflow-y: auto')
      expect(footer).toContain('flex-shrink: 0')
    },
  )

  it.each(['AppointmentView.vue', 'ManagerAppointmentView.vue'])(
    '%s omits the redundant grey-slot explanation',
    (filename) => {
      const source = readFileSync(
        resolve(process.cwd(), `src/features/appointments/views/${filename}`),
        'utf8',
      )

      expect(source).not.toContain('ช่วงสีเทา')
    },
  )
})
