import { initializeApp } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js";
import {
  getAuth, GoogleAuthProvider, signInWithPopup,
  createUserWithEmailAndPassword, signInWithEmailAndPassword,
  onAuthStateChanged, signOut
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js";

const app = initializeApp({
  apiKey: "AIzaSyAWNrVm_C_K6usyGbCd8atjKd3ISyGHIG8",
  authDomain: "apexexchange-b5467.firebaseapp.com",
  projectId: "apexexchange-b5467",
  storageBucket: "apexexchange-b5467.firebasestorage.app",
  messagingSenderId: "1067881983186",
  appId: "1:1067881983186:web:9a5804228cea9285b4510b"
});

const auth = getAuth(app);
const modal = document.getElementById("authModal");
const message = document.getElementById("authMessage");
let mode = "login";

function showMessage(text) {
  if (message) message.textContent = text;
}

window.addEventListener("apex-login-request", () => {
  modal.hidden = false;
  showMessage("");
});

document.getElementById("authClose").onclick = () => modal.hidden = true;
document.getElementById("googleLogin").onclick = async () => {
  try {
    showMessage("Opening Google sign-in…");
    await signInWithPopup(auth, new GoogleAuthProvider());
    modal.hidden = true;
  } catch (e) {
    showMessage(e.message);
  }
};

document.getElementById("authMode").onclick = () => {
  mode = mode === "login" ? "signup" : "login";
  document.getElementById("authSubmit").textContent =
    mode === "login" ? "Log in with email" : "Create account";
  document.getElementById("authMode").textContent =
    mode === "login" ? "Need an account? Sign up" : "Already registered? Log in";
  showMessage("");
};

document.getElementById("authSubmit").onclick = async () => {
  const email = document.getElementById("authEmail").value.trim();
  const password = document.getElementById("authPassword").value;
  if (!email || !password) return showMessage("Email aur password enter karo.");
  try {
    showMessage("Please wait…");
    if (mode === "login") {
      await signInWithEmailAndPassword(auth, email, password);
    } else {
      await createUserWithEmailAndPassword(auth, email, password);
    }
    modal.hidden = true;
  } catch (e) {
    showMessage(e.message);
  }
};

document.getElementById("logoutBtn").onclick = async () => {
  await signOut(auth);
};

onAuthStateChanged(auth, async (user) => {
  window.apexUser = user;
  window.apexGetIdToken = user ? () => user.getIdToken() : null;

  const heading = document.querySelector("#profile .profile-head h2");
  const detail = document.querySelector("#profile .profile-head p");
  const login = document.getElementById("loginBtn");
  const logout = document.getElementById("logoutBtn");

  if (user) {
    if (heading) heading.textContent = user.displayName || "APEX Account";
    if (detail) detail.textContent = user.email || "Signed in";
    if (login) login.hidden = true;
    if (logout) logout.hidden = false;
  } else {
    if (heading) heading.textContent = "Welcome to APEX";
    if (detail) detail.textContent = "Sign in to manage your account";
    if (login) login.hidden = false;
    if (logout) logout.hidden = true;
  }
});
