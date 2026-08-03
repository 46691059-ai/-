import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('../views/login/index.vue'),
      meta: { title: '登录', public: true },
    },
    {
      path: '/',
      name: 'RootLayout',
      component: () => import('../layout/index.vue'),
      redirect: '/home',
      children: [
        {
          path: 'home',
          name: 'Home',
          component: () => import('../views/home/index.vue'),
          meta: { title: '系统首页', menuTitle: '首页', icon: 'HomeFilled', order: 0 },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      redirect: '/home',
      meta: { hidden: true },
    },
  ],
})

router.afterEach((to) => {
  document.title = to.meta.title
    ? `${to.meta.title} - 国企数字化治理与经营赋能平台`
    : '国企数字化治理与经营赋能平台'
})

export default router
