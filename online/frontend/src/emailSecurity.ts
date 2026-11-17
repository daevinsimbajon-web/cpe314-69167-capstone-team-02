export interface VerificationChallenge {
  ticket:string; maskedEmail:string; expiresAt:string; resendAt:string;
  purpose:'EMAIL_VERIFICATION'|'PASSWORD_RESET'; deliveryStatus:string;
}
const key='hootly-email-verification';
export function rememberVerification(value:VerificationChallenge){sessionStorage.setItem(key,JSON.stringify(value));}
export function pendingVerification():VerificationChallenge|null {
  try {const value=JSON.parse(sessionStorage.getItem(key)||'null') as VerificationChallenge|null;return value?.purpose==='EMAIL_VERIFICATION'&&typeof value.ticket==='string'?value:null;} catch {return null;}
}
export function forgetVerification(){sessionStorage.removeItem(key);}
