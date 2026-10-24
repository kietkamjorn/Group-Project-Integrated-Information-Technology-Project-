// src/router/index.js
import { createRouter, createWebHistory } from "vue-router";
import { getAccessToken, decodeJwt } from "@/libs/fetchUtils";

// Views
import Home from "@/views/Home.vue";
import ProductDetails from "@/views/ProductDetails.vue";
import NoItems from "@/views/NoItems.vue";
import Landing from "@/views/Landing.vue";
import ProductAdd from "@/views/ProductAdd.vue";
import Productedit from "@/views/Productedit.vue";
import List from "@/views/List.vue";
import Brands from "@/views/Brands.vue";
import BrandAdd from "@/views/BrandAdd.vue";
import Brandedit from "@/views/Brandedit.vue";
import Register from "@/views/Register.vue";
import SignIn from "@/views/SignIn.vue";
import Activate from "@/views/Activate.vue";
import Profile from "@/views/Profile.vue";
import ProfileEdit from "@/views/ProfileEdit.vue";
import Cart from "@/views/Cart.vue";
import YourOrders from "@/views/YourOrders.vue";
import OrderDetails from "@/views/OrderDetails.vue";
import Signout from "@/views/Signout.vue";
import SaleOrders from "@/views/SaleOrders.vue";
import SaleDetails from "@/views/SaleDetails.vue";
import ForgotPassword from "@/views/ForgotPassword.vue";
import ResetPassword from "@/views/ResetPassword.vue";
import ChangePassword from "@/views/ChangePassword.vue";

const router = createRouter({
  history: createWebHistory("/kp4/"),
  routes: [
    { path: "/", name: "Landing", component: Landing },

    // Buyer catalog
    { path: "/sale-items", name: "Home", component: Home },
    {
      path: "/sale-items/:id",
      name: "ProductDetails",
      component: ProductDetails,
      props: true,
    },

    // Seller product management
    {
      path: "/sale-items/add",
      name: "AddProduct",
      component: ProductAdd,
      meta: { requiresAuth: true },
    },
    {
      path: "/sale-items/:id/edit",
      name: "EditSaleItem",
      component: Productedit,
      props: true,
      meta: { requiresAuth: true },
    },
    {
      path: "/sale-items/list",
      name: "SaleItemsList",
      component: List,
      meta: { requiresAuth: true, blockRoles: ["BUYER"] },
    },

    // Brands
    { path: "/brands", name: "Brands", component: Brands },
    { path: "/brands/add", name: "BrandAdd", component: BrandAdd },
    {
      path: "/brands/:id",
      name: "Brandedit",
      component: Brandedit,
      props: true,
    },

    // Auth
    { path: "/register", name: "Register", component: Register },
    { path: "/signin", name: "Signin", component: SignIn },
    { path: "/verify-email", name: "VerifyEmail", component: Activate },
    { path: "/signout", name: "SignOut", component: Signout },

    // Password flows
    {
      path: "/forgot-password",
      name: "ForgotPassword",
      component: ForgotPassword,
    },
    {
      path: "/reset-password",
      name: "ResetPassword",
      component: ResetPassword,
    },

    // Profile
    {
      path: "/profile",
      name: "Profile",
      component: Profile,
      meta: { requiresAuth: true },
    },
    { path: "/profile/edit", name: "ProfileEdit", component: ProfileEdit },
    {
      path: "/change-password",
      name: "ChangePassword",
      component: ChangePassword,
      meta: { requiresAuth: true },
    },

    // Buyer order pages
    { path: "/cart", name: "Cart", component: Cart },
    { path: "/your-orders", name: "YourOrders", component: YourOrders },
    {
      path: "/your-orders/:orderId",
      name: "YourOrderDetail",
      component: OrderDetails,
      props: true,
    },

    // Seller order pages
    { path: "/sale-orders", name: "SaleOrders", component: SaleOrders },
    {
      path: "/sale-orders/:orderId",
      name: "SaleOrderDetail",
      component: SaleDetails,
      props: true,
    },

    // Fallback
    { path: "/items", name: "NotItems", component: NoItems },
    { path: "/:pathMatch(.*)*", redirect: "/sale-items" },
  ],
});

// ---------- Route Guards (สั้น-ชัด) ----------
function getRoleAndAuth() {
  try {
    const t = getAccessToken();
    if (!t) return { role: "GUEST", authed: false };
    const p = decodeJwt(t) || {};
    return { role: String(p.role || "GUEST").toUpperCase(), authed: true };
  } catch {
    return { role: "GUEST", authed: false };
  }
}

router.beforeEach((to, _from, next) => {
  const { role, authed } = getRoleAndAuth();

  if (authed && role === "SELLER" && to.path === "/") {
    return next({ name: "SaleItemsList" });
  }

  // ต้องล็อกอิน?
  if (to.meta?.requiresAuth && !authed) {
    const redirect =
      to.fullPath && to.fullPath !== "/"
        ? { redirect: to.fullPath }
        : undefined;
    return next({ name: "Signin", query: redirect });
  }

  // บล็อกตาม role
  const blocked = to.meta?.blockRoles;
  if (
    Array.isArray(blocked) &&
    blocked.map((r) => String(r).toUpperCase()).includes(role)
  ) {
    return next({ path: "/sale-items" });
  }

  next();
});

export default router;
