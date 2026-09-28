import { Routes } from '@angular/router';

import { CreateOrder } from './orders/create-order/create-order';

export const routes: Routes = [
  { path: '', component: CreateOrder, title: 'FoodFlow — Nuevo pedido' },
  { path: '**', redirectTo: '' },
];
