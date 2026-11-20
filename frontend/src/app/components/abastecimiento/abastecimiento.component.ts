import { CommonModule } from '@angular/common';
import { Component, HostListener, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { HttpClient, HttpParams } from '@angular/common/http';
import { AuthService } from '../../services/auth.service';
import { DataSyncService } from '../../services/data-sync.service';
import { ConfirmationDialogComponent } from '../shared/confirmation-dialog.component';
import { formatDateTimeLima } from '../../utils/date-time-lima';

interface Supplier { idProveedor:number; documento:string; nombre:string; contacto?:string; telefono?:string; correo?:string; estado:boolean; }
interface ProductOption { idProducto:number; codigo:string; nombre:string; estado:boolean; }
interface Purchase { idCompra:number; idProveedor:number; proveedor:string; referencia:string; estado:'PENDIENTE'|'RECIBIDA'; fecha:string; detalles:{idProducto:number;producto:string;cantidad:number}[]; }
@Component({selector:'app-abastecimiento',standalone:true,imports:[CommonModule,FormsModule,ConfirmationDialogComponent],templateUrl:'./abastecimiento.component.html',styleUrl:'./abastecimiento.component.scss'})
export class AbastecimientoComponent implements OnInit {
  mode:'proveedores'|'compras'='proveedores'; suppliers=signal<Supplier[]>([]); purchases=signal<Purchase[]>([]); products=signal<ProductOption[]>([]); busy=signal(false); error=signal(''); message=signal(''); query='';
  doc=''; name=''; contact=''; phone=''; email=''; supplierId:number|null=null;
  ref=''; selectedSupplier:number|null=null; purchaseId:number|null=null; lines:{idProducto:number|null;cantidad:number}[]=[{idProducto:null,cantidad:1}];
  supplierToDeactivate=signal<Supplier|null>(null); pendingReceive=signal<Purchase|null>(null); private stopSync?:()=>void; private supplierDialogTrigger:HTMLElement|null=null;
  readonly sedeId:number;
  get siteName():string{return this.auth.currentUser()?.nombreSede||('Sede '+this.sedeId);}
  formatDate(value:string|null|undefined):string{return formatDateTimeLima(value);}
  canReadSupply(){return this.auth.hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','ENCARGADO_COMPRAS');}
  canManageSupply(){return this.auth.hasAnyRole('ADMINISTRADOR','ENCARGADO_COMPRAS');}
  constructor(private route:ActivatedRoute,private http:HttpClient,private auth:AuthService,private sync:DataSyncService){this.sedeId=auth.activeSedeId()||auth.currentUser()?.idSede||0;}
  ngOnInit(){this.stopSync=this.sync.listen(['all','supply','inventory','operations'],()=>{this.load();this.loadPurchaseOptions();});this.route.data.subscribe(data=>{this.mode=data['mode']==='compras'?'compras':'proveedores';this.load();this.loadPurchaseOptions();});}
  ngOnDestroy(){this.stopSync?.();}
  private loadPurchaseOptions(){if(this.mode==='compras'&&this.canManageSupply()){this.http.get<Supplier[]>('/api/proveedores',{params:{activos:'true'}}).subscribe({next:v=>this.suppliers.set(v),error:e=>this.fail(e)});this.http.get<ProductOption[]>('/api/productos').subscribe({next:v=>this.products.set(v.filter(p=>p.estado)),error:e=>this.fail(e)});}}
  load(){this.error.set('');this.busy.set(true);if(this.mode==='proveedores'){let p=new HttpParams();if(this.query.trim())p=p.set('query',this.query.trim());this.http.get<Supplier[]>('/api/proveedores',{params:p}).subscribe({next:v=>{this.suppliers.set(v);this.busy.set(false)},error:e=>this.fail(e)});}else{let p=new HttpParams().set('sedeId',String(this.sedeId));if(this.query.trim())p=p.set('query',this.query.trim());this.http.get<Purchase[]>('/api/compras',{params:p}).subscribe({next:v=>{this.purchases.set(v);this.busy.set(false)},error:e=>this.fail(e)});}}
  saveSupplier(){this.error.set('');const payload={documento:this.doc.trim(),nombre:this.name.trim(),contacto:this.contact.trim(),telefono:this.phone.trim(),correo:this.email.trim()};if(!/^\d{11}$/.test(payload.documento)){this.error.set('El RUC debe contener exactamente 11 dígitos.');return;}if(!payload.nombre){this.error.set('La razón social es obligatoria.');return;}this.busy.set(true);const req=this.supplierId?this.http.put<Supplier>(`/api/proveedores/${this.supplierId}`,payload):this.http.post<Supplier>('/api/proveedores',payload);req.subscribe({next:()=>{this.sync.announce('supply');this.resetSupplier();this.message.set('Proveedor guardado.');this.load()},error:e=>this.fail(e)});}
  editSupplier(p:Supplier){this.supplierId=p.idProveedor;this.doc=p.documento;this.name=p.nombre;this.contact=p.contacto||'';this.phone=p.telefono||'';this.email=p.correo||'';}
  requestSupplierDeactivation(p:Supplier){this.supplierDialogTrigger=document.activeElement as HTMLElement;this.supplierToDeactivate.set(p);queueMicrotask(()=>document.querySelector<HTMLElement>('.confirm-dialog button')?.focus());}
  cancelSupplierDeactivation(){this.supplierToDeactivate.set(null);this.supplierDialogTrigger?.focus();this.supplierDialogTrigger=null;}
  confirmSupplierDeactivation(){const p=this.supplierToDeactivate();if(!p)return;this.busy.set(true);this.http.patch(`/api/proveedores/${p.idProveedor}/estado`,null,{params:{activo:'false'}}).subscribe({next:()=>{this.supplierToDeactivate.set(null);this.supplierDialogTrigger?.focus();this.supplierDialogTrigger=null;this.message.set('Proveedor inactivado. Se conservó su historial.');this.sync.announce('supply');this.load();this.loadPurchaseOptions();},error:e=>this.fail(e)});}
  activateSupplier(p:Supplier){this.busy.set(true);this.http.patch(`/api/proveedores/${p.idProveedor}/estado`,null,{params:{activo:'true'}}).subscribe({next:()=>{this.message.set('Proveedor activado.');this.sync.announce('supply');this.load();this.loadPurchaseOptions();},error:e=>this.fail(e)});}
  resetSupplier(){this.supplierId=null;this.doc=this.name=this.contact=this.phone=this.email='';}
  addLine(){this.lines=[...this.lines,{idProducto:null,cantidad:1}]}; removeLine(i:number){if(this.lines.length>1)this.lines=this.lines.filter((_,n)=>n!==i);}
  savePurchase(){const valid=this.lines.filter(l=>l.idProducto&&l.cantidad>0);if(!this.sedeId||!this.selectedSupplier||!this.ref.trim()||valid.length!==this.lines.length||!valid.length||new Set(valid.map(l=>l.idProducto)).size!==valid.length){this.error.set('Completa referencia, proveedor y productos sin repetir.');return;}const body={idSede:this.sedeId,idProveedor:this.selectedSupplier,referencia:this.ref.trim(),detalles:valid.map(l=>({idProducto:Number(l.idProducto),cantidad:Number(l.cantidad)}))};this.error.set('');this.busy.set(true);const req=this.purchaseId?this.http.put(`/api/compras/${this.purchaseId}`,body):this.http.post('/api/compras',body);req.subscribe({next:()=>{this.sync.announce('supply');this.resetPurchase();this.message.set('Compra guardada. No afecta stock hasta la recepción.');this.load()},error:e=>this.fail(e)});}
  editPurchase(c:Purchase){this.purchaseId=c.idCompra;this.ref=c.referencia;this.selectedSupplier=c.idProveedor;this.lines=c.detalles.map(d=>({idProducto:d.idProducto,cantidad:d.cantidad}));}
  receive(c:Purchase){this.error.set('');this.pendingReceive.set(c);}
  cancelReceive(){if(!this.busy())this.pendingReceive.set(null);}
  confirmReceive(){const c=this.pendingReceive();if(!c||this.busy())return;this.busy.set(true);this.error.set('');this.http.post('/api/compras/' + c.idCompra + '/recepcion',null,{params:{sedeId:String(this.sedeId)}}).subscribe({next:()=>{this.pendingReceive.set(null);this.sync.announce('all');this.message.set('Recepcion registrada como ingreso de inventario.');this.load()},error:e=>this.fail(e)});}
  resetPurchase(){this.purchaseId=null;this.ref='';this.selectedSupplier=null;this.lines=[{idProducto:null,cantidad:1}];}
  @HostListener('document:keydown',['$event']) onSupplierDialogKeydown(event:KeyboardEvent){if(!this.supplierToDeactivate())return;const dialog=document.querySelector<HTMLElement>('.confirm-dialog');if(!dialog)return;if(event.key==='Escape'){event.preventDefault();this.cancelSupplierDeactivation();return;}if(event.key!=='Tab')return;const controls=Array.from(dialog.querySelectorAll<HTMLElement>('button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])'));if(!controls.length){event.preventDefault();dialog.focus();return;}const first=controls[0],last=controls[controls.length-1];if(event.shiftKey&&(document.activeElement===first||!dialog.contains(document.activeElement))){event.preventDefault();last.focus();}else if(!event.shiftKey&&(document.activeElement===last||!dialog.contains(document.activeElement))){event.preventDefault();first.focus();}}
  private fail(e:any){this.busy.set(false);this.error.set(e?.error?.error||'No se pudo completar la solicitud.');}
}
