// src/libs/signoutEverywhere.js
import {
  logout as beLogout,
  clearTokens as clearAccessToken,
} from "@/libs/fetchUtils";
import { auth } from "@/libs/Auth";

const NICK_KEY = "itbms:nickname";

export async function signOutEverywhere() {
  try {
    await beLogout(); 
  } catch {}
  try {
    auth.clear?.();
  } catch {}
  try {
    clearAccessToken?.();
  } catch {}
  try {
    localStorage.removeItem(NICK_KEY);
    Object.keys(localStorage)
      .filter((k) => k.startsWith("cart:v1:"))
      .forEach((k) => localStorage.removeItem(k));
  } catch {}
  try {
    window.dispatchEvent(new Event("itbms:cart-updated"));
    window.dispatchEvent(new Event("itbms:auth-changed"));
  } catch {}

  return true;
}
