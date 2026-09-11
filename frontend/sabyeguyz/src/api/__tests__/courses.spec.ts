import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createCourse, deactivateCourse, listCourses } from '../courses'

describe('courses api', () => {
  beforeEach(() => vi.restoreAllMocks())
  it('lists courses', async () => { vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve([]) })); await expect(listCourses()).resolves.toEqual([]) })
  it('creates a course', async () => { const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve({ id: 1 }) }); vi.stubGlobal('fetch', fetchMock); await createCourse({ code: 'PKG1', name: 'Course', price: 100, sessions: 5, bonusSessions: 0, validityDays: 30 }); expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/courses', expect.objectContaining({ method: 'POST' })) })
  it('deactivates a course', async () => { const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 204 }); vi.stubGlobal('fetch', fetchMock); await deactivateCourse(1); expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/courses/1', expect.objectContaining({ method: 'DELETE' })) })
})
