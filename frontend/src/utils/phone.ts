export const limitPhoneDigits = (value: string, maxDigits = 10) =>
  value.replace(/\D/g, '').slice(0, maxDigits)

export const isOptionalPhoneValid = (value: string) => value === '' || /^[0-9]{1,10}$/.test(value)

export const isContactPhoneValid = (value: string) => /^[0-9]{1,10}$/.test(value)

export const isBranchPhoneValid = (value: string) => /^[0-9]{9,10}$/.test(value)
