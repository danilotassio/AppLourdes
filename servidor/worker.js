// Servidor de avisos do AppLourdinha (Cloudflare Worker)
// Bindings: KV = namespace "lourdinha"; secret FCM_SA = JSON da conta de servico do Firebase.
const J = (o, s = 200) => new Response(JSON.stringify(o), { status: s, headers: { "content-type": "application/json" } });
const okCode = c => typeof c === "string" && /^[A-Za-z0-9_-]{20,64}$/.test(c);
const b64u = b => btoa(String.fromCharCode(...new Uint8Array(b))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const enc = s => b64u(new TextEncoder().encode(s));

async function accessToken(env) {
  const cached = await env.KV.get("oauth", "json");
  if (cached && cached.exp > Date.now() + 60000) return cached.t;
  const sa = JSON.parse(env.FCM_SA), now = Math.floor(Date.now() / 1000);
  const head = enc(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = enc(JSON.stringify({ iss: sa.client_email, scope: "https://www.googleapis.com/auth/firebase.messaging", aud: sa.token_uri, iat: now, exp: now + 3600 }));
  const pem = sa.private_key.replace(/-----[A-Z ]+-----/g, "").replace(/\s+/g, "");
  const key = await crypto.subtle.importKey("pkcs8", Uint8Array.from(atob(pem), c => c.charCodeAt(0)), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const sig = b64u(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(head + "." + claim)));
  const r = await fetch(sa.token_uri, { method: "POST", headers: { "content-type": "application/x-www-form-urlencoded" }, body: "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=" + head + "." + claim + "." + sig });
  const j = await r.json();
  await env.KV.put("oauth", JSON.stringify({ t: j.access_token, exp: Date.now() + 3300000 }), { expirationTtl: 3500 });
  return j.access_token;
}

export default {
  async fetch(req, env) {
    if (req.method !== "POST") return J({ ok: true });
    const url = new URL(req.url);
    let b; try { b = await req.json(); } catch (e) { return J({ erro: "json" }, 400); }
    if (!okCode(b.c)) return J({ erro: "codigo" }, 400);
    const k = "c:" + b.c;
    let list = (await env.KV.get(k, "json")) || [];
    if (url.pathname === "/reg") {              // cuidador registra o aparelho
      if (typeof b.t !== "string" || b.t.length < 20 || b.t.length > 400) return J({ erro: "token" }, 400);
      if (!list.includes(b.t)) list.push(b.t);
      list = list.slice(-10);
      await env.KV.put(k, JSON.stringify(list));
      return J({ ok: true, n: list.length });
    }
    if (url.pathname === "/unreg") {            // cuidador sai
      list = list.filter(x => x !== b.t); await env.KV.put(k, JSON.stringify(list)); return J({ ok: true });
    }
    if (url.pathname === "/send") {             // celular da Lourdinha envia um aviso
      const title = String(b.title || "AppLourdinha").slice(0, 80), body = String(b.body || "").slice(0, 400), kind = String(b.kind || "info").slice(0, 20);
      if (!list.length) return J({ ok: true, n: 0 });
      const tok = await accessToken(env), pid = JSON.parse(env.FCM_SA).project_id;
      let sent = 0; const keep = [];
      for (const t of list) {
        const r = await fetch("https://fcm.googleapis.com/v1/projects/" + pid + "/messages:send", {
          method: "POST", headers: { authorization: "Bearer " + tok, "content-type": "application/json" },
          body: JSON.stringify({ message: { token: t, data: { title, body, kind, ts: String(Date.now()) }, android: { priority: "HIGH" } } })
        });
        if (r.ok) { sent++; keep.push(t); } else if (r.status !== 404 && r.status !== 400) keep.push(t);
      }
      if (keep.length !== list.length) await env.KV.put(k, JSON.stringify(keep));
      return J({ ok: true, n: sent });
    }
    return J({ erro: "rota" }, 404);
  }
};
