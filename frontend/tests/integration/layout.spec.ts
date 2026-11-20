import { expect, test, request } from '@playwright/test';

const backend = 'http://127.0.0.1:18080';

test('el menú lateral navega por permisos, conserva el estado activo y se comporta como drawer en móvil', async ({ page }) => {
  const api = await request.newContext();
  const response = await api.post(`${backend}/api/auth/login`, {
    data: { usuario: process.env.TOTTUS_E2E_USERNAME, password: process.env.TOTTUS_E2E_PASSWORD }
  });
  expect(response.ok()).toBeTruthy();
  const user = await response.json();
  await page.addInitScript(value => {
    localStorage.setItem('tottus_token', value.token);
    localStorage.setItem('tottus_user', JSON.stringify(value));
    localStorage.setItem('tottus_active_sede', String(value.idSede));
  }, user);

  await page.setViewportSize({ width: 1440, height: 960 });
  await page.goto('/dashboard');
  if (user.rol === 'ADMINISTRADOR' && user.accesoGlobal === true) {
    await expect(page.getByRole('combobox', { name: 'Sede activa' })).toBeVisible();
  } else {
    await expect(page.getByRole('combobox', { name: 'Sede activa' })).toHaveCount(0);
    await expect(page.locator('.sede-value')).toBeVisible();
    await expect(page.locator('.sede-value')).toContainText(user.nombreSede);
  }
  const navigation = page.getByRole('navigation', { name: 'Módulos del sistema' });
  await expect(navigation.getByRole('link', { name: 'Dashboard' })).toHaveClass(/active/);
  await expect(page.locator('.module-nav')).toHaveCount(0);
  await page.getByRole('button', { name: 'Contraer menú' }).click();
  await expect(page.locator('.side-nav')).toHaveClass(/collapsed/);
  await page.getByRole('button', { name: 'Expandir menú' }).click();
  await navigation.getByRole('link', { name: 'Compras' }).click();
  await expect(page).toHaveURL(/\/compras$/);

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/dashboard');
  const menuButton = page.getByRole('button', { name: 'Abrir menú' });
  await menuButton.click();
  await expect(menuButton).toHaveAttribute('aria-expanded', 'true');
  await page.getByRole('navigation', { name: 'Módulos del sistema' }).getByRole('link', { name: 'Proveedores' }).click();
  await expect(page).toHaveURL(/\/proveedores$/);
  await expect(page.locator('.side-nav')).not.toHaveClass(/open/);
  await api.dispose();
});
