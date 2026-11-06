import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/guards/auth.guards';

// loadComponent = lazy loading: each page is a separate JS chunk downloaded
// only when the user first visits it.
export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
    title: 'Sign in',
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/register.component').then((m) => m.RegisterComponent),
    title: 'Create account',
  },
  {
    path: 'forgot-password',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/forgot-password.component').then((m) => m.ForgotPasswordComponent),
    title: 'Forgot password',
  },
  {
    // reachable signed in or out: it is the target of emailed links (password reset, doctor invitation)
    path: 'reset-password',
    loadComponent: () =>
      import('./features/auth/reset-password.component').then((m) => m.ResetPasswordComponent),
    title: 'Choose a new password',
  },
  {
    path: 'verify-email',
    loadComponent: () =>
      import('./features/auth/verify-email.component').then((m) => m.VerifyEmailComponent),
    title: 'Verify your email',
  },
  {
    // Shell = navbar + <router-outlet>. Everything inside requires login.
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell.component').then((m) => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
        title: 'Dashboard',
      },
      {
        path: 'appointments',
        loadComponent: () =>
          import('./features/appointments/appointment-list.component').then(
            (m) => m.AppointmentListComponent,
          ),
        title: 'Appointments',
      },
      {
        path: 'appointments/:id/messages',
        loadComponent: () =>
          import('./features/messages/messages.component').then((m) => m.MessagesComponent),
        title: 'Messages',
      },
      {
        path: 'admin/doctors',
        canActivate: [roleGuard('ADMIN')],
        loadComponent: () =>
          import('./features/admin/admin-doctors.component').then((m) => m.AdminDoctorsComponent),
        title: 'Manage doctors',
      },
      {
        path: 'chat',
        canActivate: [roleGuard('PATIENT')],
        loadComponent: () => import('./features/chat/chat.component').then((m) => m.ChatComponent),
        title: 'Chat with Aria',
      },
      {
        path: 'risk',
        canActivate: [roleGuard('PATIENT')],
        loadComponent: () => import('./features/risk/risk.component').then((m) => m.RiskComponent),
        title: 'Risk check',
      },
      {
        path: 'recommend',
        canActivate: [roleGuard('PATIENT')],
        loadComponent: () =>
          import('./features/recommend/recommend.component').then((m) => m.RecommendComponent),
        title: 'Find a doctor',
      },
      {
        path: 'appointments/new',
        canActivate: [roleGuard('PATIENT')],
        loadComponent: () =>
          import('./features/appointments/appointment-form.component').then(
            (m) => m.AppointmentFormComponent,
          ),
        title: 'Book appointment',
      },
    ],
  },
  {
    path: '**',
    loadComponent: () =>
      import('./features/not-found/not-found.component').then((m) => m.NotFoundComponent),
    title: 'Not found',
  },
];
