import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./features/rest-dashboard/rest-dashboard').then((module) => module.RestDashboardComponent),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
