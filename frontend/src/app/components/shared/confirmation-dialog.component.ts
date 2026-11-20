import { AfterViewInit, Component, ElementRef, EventEmitter, HostListener, Input, OnDestroy, Output, ViewChild } from '@angular/core';

@Component({
  selector: 'app-confirmation-dialog',
  standalone: true,
  templateUrl: './confirmation-dialog.component.html',
  styleUrl: './confirmation-dialog.component.scss'
})
export class ConfirmationDialogComponent implements AfterViewInit, OnDestroy {
  private static nextId = 0;
  readonly titleId = `confirmation-dialog-title-${ConfirmationDialogComponent.nextId++}`;
  private readonly returnFocusTo = document.activeElement instanceof HTMLElement ? document.activeElement : null;

  @Input({ required: true }) title = '';
  @Input() message = '';
  @Input() confirmText = 'Confirmar';
  @Input() busy = false;
  @Input() tone: 'primary' | 'danger' = 'primary';
  @Input() restoreFocus = true;
  @ViewChild('dialog') private dialog?: ElementRef<HTMLElement>;
  @Output() confirmed = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();

  ngAfterViewInit(): void {
    window.setTimeout(() => {
      const first = this.focusableElements()[0];
      if (first) first.focus();
      else this.dialog?.nativeElement.focus();
    });
  }

  ngOnDestroy(): void {
    if (!this.restoreFocus) return;
    const target = this.returnFocusTo;
    window.setTimeout(() => target?.focus());
  }

  cancel(event?: Event): void {
    if (this.busy) return;
    event?.stopPropagation();
    this.cancelled.emit();
  }

  confirm(): void {
    if (!this.busy) this.confirmed.emit();
  }

  @HostListener('document:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.cancel();
      return;
    }
    if (event.key !== 'Tab') return;
    const controls = this.focusableElements();
    if (!controls.length) {
      event.preventDefault();
      this.dialog?.nativeElement.focus();
      return;
    }
    const first = controls[0];
    const last = controls[controls.length - 1];
    if (event.shiftKey && (document.activeElement === first || !this.dialog?.nativeElement.contains(document.activeElement))) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && (document.activeElement === last || !this.dialog?.nativeElement.contains(document.activeElement))) {
      event.preventDefault();
      first.focus();
    }
  }

  private focusableElements(): HTMLElement[] {
    return Array.from(this.dialog?.nativeElement.querySelectorAll<HTMLElement>(
      'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
    ) ?? []).filter(element => element.getAttribute('aria-hidden') !== 'true');
  }
}
