package pe.joedayz.usuarios.servicios;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import pe.joedayz.usuarios.modelos.Imagen;
import pe.joedayz.usuarios.modelos.Usuario;
import pe.joedayz.usuarios.modelos.Usuarios;
import pe.joedayz.usuarios.modelos.rest.Link;

@Stateless
public class UsuariosService implements UsuariosServiceInterface {

   @PersistenceContext
   private EntityManager em;

   @Override
   public Response listarUsuarios(Date modifiedSince, Integer inicio,
         Integer tamanhoPagina, UriInfo uriInfo) {
      Collection<Usuario> usuarios = em
            .createQuery("select u from Usuario u", Usuario.class)
            .setFirstResult(inicio).setMaxResults(tamanhoPagina.intValue())
            .getResultList();

      // Recuperamos el numero de usuarios presentes en nuestra base
      // para que podamos realizar el calculo de paginas
      Long numeroUsuarios = em.createQuery("select count(u) from Usuario u",
            Long.class).getSingleResult();

      boolean actualizado = false;

      if (modifiedSince != null) {
         for (Usuario usuario : usuarios) {
            if (usuario.getFechaActualizacion().after(modifiedSince)) {
               actualizado = true;
               break;
            }
         }
      } else {

         actualizado = true;
      }

      if (actualizado) {

         for (Usuario usuario : usuarios) {
            Link link = crearLinkImagenUsuario(usuario);
            usuario.agregarLink(link);
         }

         return Response.ok(
               new Usuarios(usuarios, crearLinksUsuarios(uriInfo,
                     tamanhoPagina, inicio, numeroUsuarios))).build();
      } else {
         return Response.notModified().build();
      }

   }

   @Override
   public Response find(Long id, Date modifiedSince) {
      Usuario usuario = em.find(Usuario.class, id);

      if (usuario != null) {
         if (modifiedSince == null
               || (modifiedSince != null && usuario.getFechaActualizacion().after(
                     modifiedSince))) {
            usuario.agregarLink(crearLinkImagenUsuario(usuario));
            return Response.ok(usuario).build();
         }

         return Response.notModified().build();
      }
      return Response.status(Status.NOT_FOUND).build();
   }

   @Override
   public Response create(UriInfo uriInfo, Usuario usuario) {
      em.persist(usuario);


      UriBuilder uriBuilder = uriInfo.getAbsolutePathBuilder();
      URI location = uriBuilder.path("/{id}").build(usuario.getId());

      return Response.created(location).build();
   }

   @Override
   public Response update(Usuario usuario) {
      usuario = em.merge(usuario);
      return Response.noContent().build();
   }

   @Override
   public Response update(Long id, Usuario usuario) {
      usuario.setId(id);
      return update(usuario);
   }

   @Override
   public Response delete(Usuario usuario) {
      usuario = em.find(Usuario.class, usuario.getId());
      if (usuario == null) {
         return Response.status(Status.NOT_FOUND).build();
      }
      em.remove(usuario);
      return Response.noContent().build();
   }

   @Override
   public Response delete(Long id) {
      Usuario usuario = new Usuario();
      usuario.setId(id);
      return delete(usuario);
   }

   @Override
   public Response agregarImagen(String descripcion, Long idUsuario,
         HttpServletRequest httpServletRequest, byte[] datosImagen) {
      Usuario usuario = em.find(Usuario.class, idUsuario);
      if (usuario == null) {
         return Response.status(Status.NOT_FOUND).build();
      }
      Imagen imagem = new Imagen();
      imagem.setDatos(datosImagen);
      imagem.setDescripcion(descripcion);
      imagem.setTipo(httpServletRequest.getContentType());
      usuario.setImagen(imagem);
      em.merge(usuario);
      return Response.noContent().build();
   }

   @Override
   public Response recuperarImagen(Long id, Date modifiedSince) {
      Usuario usuario = em.find(Usuario.class, id);
      if (usuario == null) {
         return Response.status(Status.NOT_FOUND).build();
      }
      Imagen imagem = usuario.getImagen();

      if (modifiedSince != null
            && imagem.getFechaActualizacion().before(modifiedSince)) {
         return Response.notModified().build();
      }

      return Response.ok(imagem.getDatos(), imagem.getTipo())
            .header(CAMPO_DESCRIPCION_IMAGEN, imagem.getDescripcion()).build();
   }

   private Link crearLinkImagenUsuario(Usuario usuario) {
      String uri = UriBuilder.fromPath("usuarios/{id}").build(usuario.getId())
            .toString();
      String rel = "imagen";
      String type = "image/*";

      return new Link(uri, rel, type);
   }

   private Link[] crearLinksUsuarios(UriInfo uriInfo, Integer tamanhoPagina,
         Integer inicio, Long numeroUsuarios) {
      Collection<Link> links = new ArrayList<>();

      double numeroUsuariosDouble = numeroUsuarios;
      double tamanhoPaginaDouble = tamanhoPagina;

      // Obtener el numero correcto de paginas
      Long numeroPaginas = (long) Math.ceil(numeroUsuariosDouble
            / tamanhoPaginaDouble);

      // El resultado de la divisi�n sera un int.
      Long paginaAtual = new Long(inicio / tamanhoPagina);

      Link linkPrimeiraPagina = new Link(
            UriBuilder.fromPath(uriInfo.getPath()).queryParam(PARAM_INICIO, 0)
                  .queryParam(PARAM_TAMANHO_PAGINA, tamanhoPagina).build()
                  .toString(), "primeraPagina");
      links.add(linkPrimeiraPagina);

      if (paginaAtual > 0) {
         if (paginaAtual <= numeroPaginas) {
            Link linkPaginaAnterior = new Link(UriBuilder
                  .fromPath(uriInfo.getPath())
                  .queryParam(PARAM_INICIO, (paginaAtual - 1) * tamanhoPagina)
                  .queryParam(PARAM_TAMANHO_PAGINA, tamanhoPagina).build()
                  .toString(), "paginaAnterior");
            links.add(linkPaginaAnterior);
         } else {
            Link linkPaginaAnterior = new Link(
                  UriBuilder
                        .fromPath(uriInfo.getPath())
                        .queryParam(PARAM_INICIO,
                              (numeroPaginas - 1) * tamanhoPagina)
                        .queryParam(PARAM_TAMANHO_PAGINA, tamanhoPagina)
                        .build().toString(), "paginaAnterior");
            links.add(linkPaginaAnterior);
         }
      }

      if (paginaAtual < (numeroPaginas - 1)) {
         Link linkProximaPagina = new Link(UriBuilder
               .fromPath(uriInfo.getPath())
               .queryParam(PARAM_INICIO, (paginaAtual + 1) * tamanhoPagina)
               .queryParam(PARAM_TAMANHO_PAGINA, tamanhoPagina).build()
               .toString(), "proximaPagina");
         links.add(linkProximaPagina);
      }

      Link linkUltimaPagina = new Link(
            UriBuilder
                  .fromPath(uriInfo.getPath())
                  .queryParam(PARAM_INICIO, (numeroPaginas - 1) * tamanhoPagina)
                  .queryParam(PARAM_TAMANHO_PAGINA, tamanhoPagina).build()
                  .toString(), "ultimaPagina");
      links.add(linkUltimaPagina);

      return links.toArray(new Link[] {});
   }
}
