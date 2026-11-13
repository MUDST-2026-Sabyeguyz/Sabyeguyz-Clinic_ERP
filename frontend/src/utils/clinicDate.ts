// "Today" for the clinic is the date in Bangkok, not the UTC date (they differ between 00:00 and 07:00 Bangkok time).
const bangkokDate = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Bangkok',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

export function clinicToday(): string {
  return bangkokDate.format(new Date())
}

export function clinicYesterday(): string {
  const [year = 1970, month = 1, day = 1] = clinicToday().split('-').map(Number)
  const yesterday = new Date(Date.UTC(year, month - 1, day - 1))
  return yesterday.toISOString().slice(0, 10)
}
