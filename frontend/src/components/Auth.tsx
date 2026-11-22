import React, { useState } from "react";
import { ChefHat, LogIn } from "lucide-react";
import { api } from "../api";
import { GENERIC_ERROR } from "../lib";
import type { SessionUser } from "../session";

export function Auth({ onLogin }: { onLogin: (token: string, user: SessionUser) => void }) {
  const [register, setRegister] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    try {
      const { data } = await api.post(
        `/auth/${register ? "register" : "login"}`,
        register ? { email, password, displayName: name } : { email, password },
      );
      onLogin(data.token, data.user);
    } catch (error) {
      console.error("[KitchenAI Web] authentication failed", error);
      window.alert(GENERIC_ERROR);
    }
  };

  return (
    <main className="center">
      <section className="card auth">
        <ChefHat size={42} />
        <h1>Kitchen AI</h1>
        <p>İyi sonuç için mutfağında akıllı bir yardımcı.</p>
        <form onSubmit={submit}>
          {register && (
            <input
              required
              placeholder="Adın"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          )}
          <input
            required
            type="email"
            placeholder="E-posta"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <input
            required
            minLength={8}
            type="password"
            placeholder="Şifre"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <button>
            <LogIn size={18} />
            {register ? "Kayıt ol" : "Giriş yap"}
          </button>
        </form>
        <button className="link" onClick={() => setRegister(!register)}>
          {register ? "Zaten hesabım var" : "Hesap oluştur"}
        </button>
      </section>
    </main>
  );
}
