import { Routes } from '@angular/router';

import { CreateOrder } from './orders/create-order/create-order';
import { OrderLookup } from './orders/order-lookup/order-lookup';
import { OrderStatusPage } from './orders/order-status/order-status';

export const routes: Routes = [
  { path: '', component: CreateOrder, title: 'FoodFlow — Nuevo pedido' },
  { path: 'orders', component: OrderLookup, title: 'FoodFlow — Consultar pedido' },
  { path: 'orders/:id', component: OrderStatusPage, title: 'FoodFlow — Estado del pedido' },
  { path: '**', redirectTo: '' },
];
