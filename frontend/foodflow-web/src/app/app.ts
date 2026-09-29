import { Component, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly title = signal('foodflow-web');
  protected readonly sidebarAbierto = signal(false);

  protected toggleSidebar(): void {
    this.sidebarAbierto.update((v) => !v);
  }

  protected cerrarSidebarEnMovil(): void {
    this.sidebarAbierto.set(false);
  }
}
