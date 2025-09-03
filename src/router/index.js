import { createRouter, createWebHistory } from "vue-router";
import Home from "../views/Home.vue";
import ProductDetails from "../views/ProductDetails.vue";
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
const history = createWebHistory('/kp4/');

const routes = [
  {
    path: "/sale-items",
    name: "Home",
    component: Home,
  },
  {
    path: "/sale-items/:id",
    name: "ProductDetails",
    component: ProductDetails,
  },
  {
    path: "/items",
    name: "NotItems",
    component: NoItems,
  },
  {
    path: "/",
    name: "Landing",
    component: Landing,
  },
  {
    path: "/sale-items/add",
    name: "AddProduct",
    component: ProductAdd,
  },
  {
    path: "/sale-items/:id/edit",
    name: "EditSaleItem",
    component: Productedit
  },
  {
    path: "/sale-items/list",
    name: "saleitemslist",
    component: List
  },
  {
    path: "/brands",
    name: "Brands",
    component: Brands
  },
  {
    path: "/brands/add",
    name: "BrandAdd",
    component: BrandAdd
  },
  {
    path: "/brands/:id",
    name: "Brandedit",
    component: Brandedit
  },
  {
    path: "/register",
    name: "Register",
    component: Register
  },
  {
    path: "/signin",
    name: "Signin",
    component: SignIn
  },
  {
    path: '/verify-email',
    name: 'VerifyEmail',
    component: Activate
  }
];

const router = createRouter({
  history,
  routes,
});

export default router;
