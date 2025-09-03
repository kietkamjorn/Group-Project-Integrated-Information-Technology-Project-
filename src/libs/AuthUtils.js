import { createData } from "./fetchUtils";

export const signIn = (email, password) => {
    return createData('/v2/users/authentications', { email, password }, {
      // ถ้าคุณจะรับ/ตั้ง cookie จาก BE ให้เปิด include
      credentials: 'include',
    });
  };