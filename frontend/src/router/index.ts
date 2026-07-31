import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/projects',
    },
    {
      path: '/projects',
      component: () => import('../layouts/ProjectLayout.vue'),
      children: [
        {
          path: '',
          name: 'ProjectList',
          component: () => import('../views/project/ProjectListView.vue'),
        },
        {
          path: 'create',
          name: 'ProjectCreate',
          component: () => import('../views/project/ProjectCreateView.vue'),
        },
        {
          path: ':id',
          name: 'ProjectDetail',
          component: () => import('../views/project/ProjectDetailView.vue'),
        },
        {
          path: ':id/edit',
          name: 'ProjectEdit',
          component: () => import('../views/project/ProjectEditView.vue'),
        },
      ],
    },
  ],
})

export default router
