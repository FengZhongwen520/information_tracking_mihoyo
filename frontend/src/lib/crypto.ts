import { api } from '../api'

const encoder = new TextEncoder()

function toBase64(bytes: Uint8Array): string {
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return btoa(binary)
}

function fromBase64(value: string): Uint8Array<ArrayBuffer> {
  const binary = atob(value)
  const bytes = new Uint8Array(new ArrayBuffer(binary.length))
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index)
  }
  return bytes
}

export async function encryptPassword(password: string): Promise<string> {
  if (!window.isSecureContext || !window.crypto?.subtle) {
    throw new Error('当前环境不支持密码加密，请使用 HTTPS 或 localhost 访问')
  }

  const { public_key: publicKey } = await api.publicKey()
  const wrappingKey = await crypto.subtle.importKey(
    'spki',
    fromBase64(publicKey),
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['encrypt'],
  )
  const contentKey = await crypto.subtle.generateKey(
    { name: 'AES-GCM', length: 256 },
    true,
    ['encrypt'],
  )
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const ciphertext = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    contentKey,
    encoder.encode(password),
  )
  const rawKey = await crypto.subtle.exportKey('raw', contentKey)
  const wrappedKey = await crypto.subtle.encrypt(
    { name: 'RSA-OAEP' },
    wrappingKey,
    rawKey,
  )

  return [
    'v1',
    toBase64(new Uint8Array(wrappedKey)),
    toBase64(iv),
    toBase64(new Uint8Array(ciphertext)),
  ].join('.')
}
