package com.tottus.inventario.service;

import com.tottus.inventario.dto.*;
import com.tottus.inventario.model.*;
import com.tottus.inventario.repository.*;
import com.tottus.inventario.security.SedeAccessService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class AbastecimientoService {
    private final ProveedorRepository proveedorRepo;
    private final CompraRepository compraRepo;
    private final DetalleCompraRepository detalleRepo;
    private final ProductoRepository productoRepo;
    private final SedeRepository sedeRepo;
    private final UsuarioRepository usuarioRepo;
    private final SedeAccessService sedeAccess;
    private final OperacionInventarioService operacionService;

    @Transactional(readOnly=true) @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','ENCARGADO_COMPRAS')")
    public List<Proveedor> listarProveedores(String query, Boolean activos) {
        List<Proveedor> all = query == null || query.isBlank() ? proveedorRepo.findAll() : proveedorRepo.findByNombreContainingIgnoreCaseOrDocumentoContainingIgnoreCaseOrderByNombre(query.trim(), query.trim());
        return all.stream().filter(p -> activos == null || Objects.equals(p.getEstado(), activos)).sorted(Comparator.comparing(Proveedor::getNombre)).toList();
    }
    @Transactional @PreAuthorize("hasAnyRole('ADMINISTRADOR','ENCARGADO_COMPRAS')")
    public Proveedor guardarProveedor(Integer id, ProveedorRequestDTO req) {
        String doc=req.getDocumento().trim();
        proveedorRepo.findByDocumento(doc).filter(p -> !Objects.equals(p.getIdProveedor(), id)).ifPresent(p -> { throw new IllegalArgumentException("Ya existe un proveedor con ese documento"); });
        Proveedor p=id==null?new Proveedor():proveedorRepo.findById(id).orElseThrow(()->new EntityNotFoundException("Proveedor no encontrado"));
        p.setDocumento(doc); p.setNombre(req.getNombre().trim()); p.setContacto(clean(req.getContacto())); p.setTelefono(clean(req.getTelefono())); p.setCorreo(clean(req.getCorreo()));
        return proveedorRepo.save(p);
    }
    @Transactional @PreAuthorize("hasAnyRole('ADMINISTRADOR','ENCARGADO_COMPRAS')")
    public Proveedor estadoProveedor(Integer id, boolean activo) {
        Proveedor p=proveedorRepo.findById(id).orElseThrow(()->new EntityNotFoundException("Proveedor no encontrado")); p.setEstado(activo); return proveedorRepo.save(p);
    }

    @Transactional(readOnly=true) @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','ENCARGADO_COMPRAS')")
    public List<Map<String,Object>> listarCompras(Integer requestedSede, String query) {
        Integer sede=sedeAccess.requireAuthorizedSede(requestedSede);
        String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        return compraRepo.findBySede_IdSedeOrderByFechaDesc(sede).stream().filter(c -> q.isEmpty() || c.getReferencia().toLowerCase(Locale.ROOT).contains(q) || String.valueOf(c.getIdCompra()).contains(q)).map(this::compraMap).toList();
    }
    @Transactional @PreAuthorize("hasAnyRole('ADMINISTRADOR','ENCARGADO_COMPRAS')")
    public Map<String,Object> crearCompra(CompraRequestDTO req, String username) {
        Integer sedeId=sedeAccess.requireAuthorizedSede(req.getIdSede());
        if(compraRepo.findByReferencia(req.getReferencia().trim()).isPresent()) throw new IllegalArgumentException("La referencia de compra ya existe");
        Proveedor supplier=proveedorRepo.findById(req.getIdProveedor()).orElseThrow(()->new EntityNotFoundException("Proveedor no encontrado"));
        if(!Boolean.TRUE.equals(supplier.getEstado())) throw new IllegalArgumentException("El proveedor está inactivo");
        Sede sede=sedeRepo.findById(sedeId).orElseThrow(()->new EntityNotFoundException("Sede no encontrada"));
        Usuario user=usuarioRepo.findByUsuario(username).orElseThrow(()->new EntityNotFoundException("Usuario no encontrado"));
        Map<Integer,CompraDetalleRequestDTO> lines=validLines(req.getDetalles());
        Map<Integer,Producto> products=productoRepo.findAllById(lines.keySet()).stream().collect(Collectors.toMap(Producto::getIdProducto,p->p));
        for(Integer id:lines.keySet()) if(!products.containsKey(id)||!Boolean.TRUE.equals(products.get(id).getEstado())) throw new EntityNotFoundException("Producto inexistente o inactivo: "+id);
        Compra c=compraRepo.save(Compra.builder().proveedor(supplier).sede(sede).usuario(user).referencia(req.getReferencia().trim()).estado("PENDIENTE").fecha(LocalDateTime.now(ZoneId.of("America/Lima"))).build());
        lines.forEach((id,line)->detalleRepo.save(DetalleCompra.builder().compra(c).producto(products.get(id)).cantidad(line.getCantidad()).costoUnitario(line.getCostoUnitario()).build()));
        return compraMap(c);
    }
    @Transactional @PreAuthorize("hasAnyRole('ADMINISTRADOR','ENCARGADO_COMPRAS')")
    public Map<String,Object> editarCompra(Integer id, CompraRequestDTO req) {
        Integer sedeId=sedeAccess.requireAuthorizedSede(req.getIdSede());
        Compra c=compraRepo.findForUpdate(id).orElseThrow(()->new EntityNotFoundException("Compra no encontrada"));
        if(!sedeId.equals(c.getSede().getIdSede())) throw new EntityNotFoundException("Compra no encontrada");
        if(!"PENDIENTE".equals(c.getEstado())) throw new IllegalArgumentException("Una compra recibida no se puede editar");
        Proveedor supplier=proveedorRepo.findById(req.getIdProveedor()).orElseThrow(()->new EntityNotFoundException("Proveedor no encontrado"));
        if(!Boolean.TRUE.equals(supplier.getEstado())) throw new IllegalArgumentException("El proveedor está inactivo");
        compraRepo.findByReferencia(req.getReferencia().trim()).filter(existing->!existing.getIdCompra().equals(id)).ifPresent(existing->{throw new IllegalArgumentException("La referencia de compra ya existe");});
        Map<Integer,CompraDetalleRequestDTO> lines=validLines(req.getDetalles());
        Map<Integer,Producto> products=productoRepo.findAllById(lines.keySet()).stream().collect(Collectors.toMap(Producto::getIdProducto,p->p));
        for(Integer pid:lines.keySet()) if(!products.containsKey(pid)||!Boolean.TRUE.equals(products.get(pid).getEstado())) throw new EntityNotFoundException("Producto inexistente o inactivo: "+pid);
        c.setProveedor(supplier); c.setReferencia(req.getReferencia().trim()); compraRepo.save(c); detalleRepo.deleteByCompra_IdCompra(id); detalleRepo.flush();
        lines.forEach((pid,line)->detalleRepo.save(DetalleCompra.builder().compra(c).producto(products.get(pid)).cantidad(line.getCantidad()).costoUnitario(line.getCostoUnitario()).build()));
        return compraMap(c);
    }
    @Transactional @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN')")
    public Map<String,Object> recibirCompra(Integer id, Integer requestedSede, String username) {
        Integer sedeId=sedeAccess.requireAuthorizedSede(requestedSede);
        Compra c=compraRepo.findForUpdate(id).orElseThrow(()->new EntityNotFoundException("Compra no encontrada"));
        if(!sedeId.equals(c.getSede().getIdSede())) throw new EntityNotFoundException("Compra no encontrada");
        if("RECIBIDA".equals(c.getEstado())) return compraMap(c);
        List<DetalleCompra> details=detalleRepo.findByCompra_IdCompraOrderByIdDetalle(id);
        OperacionRequestDTO inbound=OperacionRequestDTO.builder().idSede(sedeId).idProveedor(c.getProveedor().getIdProveedor()).referencia("RECEPCION-"+c.getReferencia())
                .detalles(details.stream().map(d->DetalleOperacionRequestDTO.builder().idProducto(d.getProducto().getIdProducto()).cantidad(d.getCantidad()).build()).toList()).build();
        OperacionResponseDTO operation=operacionService.registrarIngresoCompra(inbound,username);
        c.setEstado("RECIBIDA");
        // Link receipt by an id lookup through the public operation response.
        c.setOperacionRecepcion(com.tottus.inventario.model.OperacionInventario.builder().idOperacion(operation.getIdOperacion()).build());
        compraRepo.save(c);
        return compraMap(c);
    }
    private Map<Integer,CompraDetalleRequestDTO> validLines(List<CompraDetalleRequestDTO> details) {
        Map<Integer,CompraDetalleRequestDTO> map=new LinkedHashMap<>();
        for(CompraDetalleRequestDTO d:details) if(d==null||d.getIdProducto()==null||d.getIdProducto()<=0||d.getCantidad()==null||d.getCantidad()<=0||map.putIfAbsent(d.getIdProducto(),d)!=null) throw new IllegalArgumentException("Detalle inválido o producto repetido");
        return map;
    }
    private String clean(String s){return s==null||s.isBlank()?null:s.trim();}
    private Map<String,Object> compraMap(Compra c){
        Map<String,Object> out=new LinkedHashMap<>(); out.put("idCompra",c.getIdCompra()); out.put("idProveedor",c.getProveedor().getIdProveedor()); out.put("proveedor",c.getProveedor().getNombre()); out.put("idSede",c.getSede().getIdSede()); out.put("referencia",c.getReferencia()); out.put("estado",c.getEstado()); out.put("fecha",c.getFecha()); out.put("idOperacionRecepcion",c.getOperacionRecepcion()==null?null:c.getOperacionRecepcion().getIdOperacion());
        out.put("detalles",detalleRepo.findByCompra_IdCompraOrderByIdDetalle(c.getIdCompra()).stream().map(d->{Map<String,Object> row=new LinkedHashMap<>(); row.put("idProducto",d.getProducto().getIdProducto());row.put("codigoProducto",d.getProducto().getCodigo());row.put("producto",d.getProducto().getNombre());row.put("cantidad",d.getCantidad());row.put("costoUnitario",d.getCostoUnitario());return row;}).toList()); return out;
    }
}
