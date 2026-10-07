package crm_imobiliario.back.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import crm_imobiliario.back.model.dto.NovaSenhaDTO;
import crm_imobiliario.back.model.dto.PerfilDTO;
import crm_imobiliario.back.model.dto.TrocarSenhaDTO;
import crm_imobiliario.back.model.dto.UsuarioDTO;
import crm_imobiliario.back.model.dto.UsuarioResponse;
import crm_imobiliario.back.model.entity.Usuario;
import crm_imobiliario.back.model.service.SessaoService;
import crm_imobiliario.back.model.service.TokenService;
import crm_imobiliario.back.model.service.UsuarioService;
import crm_imobiliario.back.security.AuthCookies;
import crm_imobiliario.back.util.DefaultResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Gestão de usuários. A autorização por papel fica no SecurityFilterChain (FilterChain.java):
 * /usuarios/me e /usuarios/minha-senha para qualquer autenticado; leitura para admin/gestor;
 * todo o resto (cadastrar, atualizar, senha de terceiros, ativar/inativar) somente admin.
 */
@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private SessaoService sessaoService;

    @Autowired
    private AuthCookies authCookies;

    /** A senha temporária é devolvida uma única vez, para o admin repassar ao novo usuário. */
    @PostMapping("/cadastrar")
    public ResponseEntity<?> cadastrarCliente(
            // grupo Cadastro: valida o CPF junto com os demais campos, para todos os erros aparecerem de uma vez
            @RequestBody @org.springframework.validation.annotation.Validated({ jakarta.validation.groups.Default.class, crm_imobiliario.back.util.validacao.Cadastro.class }) UsuarioDTO dto) {
        UsuarioService.UsuarioCriado criado = usuarioService.cadastrarUsuario(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "usuario", UsuarioResponse.from(criado.usuario()),
                "senhaTemporaria", criado.senhaTemporaria()));
    }

    /**
     * Com page/size devolve uma página (Page: content, totalElements, totalPages, number, size) com busca,
     * papel e status aplicados no banco. Sem page/size devolve a lista completa — compatível com as telas
     * que só precisam de um seletor (Equipes, Redistribuição); `papel` (ex.: "gestor,admin") restringe a lista.
     */
    @GetMapping
    public ResponseEntity<?> getUsuarios(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String papel,
            @RequestParam(required = false) String status) {
        if (page == null && size == null) {
            List<UsuarioResponse> usuarios = papel == null
                    ? usuarioService.ConsultarUsuarios()
                    : usuarioService.consultarUsuarios(papel);
            return ResponseEntity.ok(usuarios);
        }
        int p = Math.max(0, page != null ? page : 0);
        int s = size != null ? Math.min(Math.max(1, size), 100) : 20;
        Page<UsuarioResponse> pg = usuarioService.listarPaginado(p, s, parseSort(sort), search, papel, status);
        return ResponseEntity.ok(pg);
    }

    /** Contadores dos cards (total, ativos, inativos) e quantidade por papel, sobre todos os usuários. */
    @GetMapping("/resumo")
    public ResponseEntity<Map<String, Object>> resumo() {
        return ResponseEntity.ok(usuarioService.resumo());
    }

    private Sort parseSort(String sort) {
        // id como desempate: sem ele, usuários com o mesmo nome podem repetir/sumir entre as páginas
        if (sort == null || sort.isBlank()) return Sort.by("nome").and(Sort.by("id"));
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        Sort.Direction dir = parts.length > 1 && parts[1].equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        // whitelist para evitar PropertyReferenceException
        if (!List.of("id", "nome", "email", "matricula", "ativo").contains(field)) {
            field = "nome";
            dir = Sort.Direction.ASC;
        }
        return Sort.by(dir, field).and(Sort.by("id"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUsuario(@PathVariable Long id) {
        return ResponseEntity.ok(UsuarioResponse.from(usuarioService.buscarPorId(id)));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(Authentication authentication) {
        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        return ResponseEntity.ok(UsuarioResponse.from(usuario));
    }

    @PutMapping("/me")
    public ResponseEntity<?> atualizarMe(@RequestBody @Valid PerfilDTO dto, Authentication authentication) {
        Usuario atualizado = usuarioService.atualizarPerfil(authentication.getName(), dto);
        // Alteração normal de perfil: papel/permissões permanecem iguais -> sem version++.
        // Novo access token (o subject é o e-mail, que pode ter mudado) só no cookie httpOnly.
        String newAccessToken = tokenService.gerarAccessToken(atualizado);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookies.access(newAccessToken).toString())
                .body(UsuarioResponse.from(atualizado));
    }

    @PutMapping("/atualizar/{id}")
    public ResponseEntity<?> atualizarUsuario(@PathVariable Long id, @RequestBody @Valid UsuarioDTO dto,
                                              Authentication authentication,
                                              @CookieValue(value = AuthCookies.ACCESS, required = false) String accessToken,
                                              HttpServletRequest request) {
        // Detecta mudança sensível de papel/permissões antes de atualizar
        Usuario antes = usuarioService.buscarPorId(id);
        String papelAntes = antes.getPapel() != null ? antes.getPapel().getPapel() : null;
        Usuario atualizado = usuarioService.atualizarUsuario(id, dto);
        String papelDepois = atualizado.getPapel() != null ? atualizado.getPapel().getPapel() : null;
        boolean papelMudou = (papelAntes == null && papelDepois != null) ||
                (papelAntes != null && !papelAntes.equals(papelDepois));
        if (papelMudou) {
            // Sensível: incrementa version e revoga refresh do alvo; invalida sessão apenas se o alvo for o próprio usuário autenticado
            usuarioService.forcarIncrementoVersion(atualizado.getId());
            sessaoService.revogarRefreshTokensDoUsuario(atualizado.getId());
            if (isSelf(authentication, atualizado)) {
                sessaoService.revogarAccessToken(tokenDaRequisicao(accessToken, request));
                return authCookies.limpar(ResponseEntity.ok())
                        .body(DefaultResponse.construir(200, "Usuário atualizado. Como o seu papel mudou, faça login novamente.", null));
            }
            return ResponseEntity.ok()
                    .body(DefaultResponse.construir(200, "Usuário atualizado. Como o papel mudou, o usuário precisará fazer login novamente.", null));
        }
        return ResponseEntity.ok(UsuarioResponse.from(atualizado));
    }

    @PutMapping("/minha-senha")
    public ResponseEntity<?> trocarMinhaSenha(@RequestBody @Valid TrocarSenhaDTO dto, Authentication authentication,
                                              @CookieValue(value = AuthCookies.ACCESS, required = false) String accessToken,
                                              HttpServletRequest request) {
        usuarioService.trocarSenha(authentication.getName(), dto.senhaAtual(), dto.novaSenha());
        // Sensível: troca de senha já incrementou tokenVersion no service; revoga refresh e limpa cookies exigindo nova autenticação
        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        sessaoService.revogarRefreshTokensDoUsuario(usuario.getId());
        sessaoService.revogarAccessToken(tokenDaRequisicao(accessToken, request));
        return authCookies.limpar(ResponseEntity.ok())
                .body(DefaultResponse.construir(HttpStatus.OK.value(), "Senha alterada com sucesso. Faça login com a nova senha.", null));
    }

    @PutMapping("/trocar-senha/{id}")
    public ResponseEntity<?> trocarSenhaUsuario(@PathVariable Long id, @RequestBody @Valid NovaSenhaDTO dto,
                                                Authentication authentication,
                                                @CookieValue(value = AuthCookies.ACCESS, required = false) String accessToken,
                                                HttpServletRequest request) {
        usuarioService.trocarSenhaAdmin(id, dto.novaSenha());
        sessaoService.revogarRefreshTokensDoUsuario(id);
        // Invalida sessão apenas se admin trocou a própria senha; para outros, apenas revoga refresh do alvo
        if (isSelf(authentication, usuarioService.buscarPorId(id))) {
            sessaoService.revogarAccessToken(tokenDaRequisicao(accessToken, request));
            return authCookies.limpar(ResponseEntity.ok())
                    .body(DefaultResponse.construir(HttpStatus.OK.value(), "Senha redefinida com sucesso. Faça login com a nova senha.", null));
        }
        return ResponseEntity.ok()
                .body(DefaultResponse.construir(HttpStatus.OK.value(), "Senha redefinida. O usuário deverá trocá-la no próximo login.", null));
    }

    /** Dados para a confirmação de inativação (gestor vinculado, leads que irão para redistribuição). */
    @GetMapping("/{id}/previa-inativacao")
    public ResponseEntity<UsuarioService.PreviaInativacao> previaInativacao(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.previaInativacao(id));
    }

    /**
     * @param gestorId   corretor sem gestor: vincula este gestor antes de inativar (ele assume a redistribuição)
     * @param semGestor  corretor sem gestor: segue sem vincular; quem inativa assume a redistribuição
     */
    @PutMapping("/inativar/{id}")
    public ResponseEntity<?> inativarUsuario(@PathVariable Long id, Authentication auth,
                                             @org.springframework.web.bind.annotation.RequestParam(required = false) Long gestorId,
                                             @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "false") boolean semGestor,
                                             @CookieValue(value = AuthCookies.ACCESS, required = false) String accessToken,
                                             HttpServletRequest request) {
        String email = auth != null ? auth.getName() : null;
        Usuario salvo = usuarioService.inativarUsuario(id, email, gestorId, semGestor);
        sessaoService.revogarRefreshTokensDoUsuario(id);
        // Se inativou a si mesmo, limpar sessão
        if (isSelf(auth, salvo)) {
            sessaoService.revogarAccessToken(tokenDaRequisicao(accessToken, request));
            return authCookies.limpar(ResponseEntity.ok()).body(UsuarioResponse.from(salvo));
        }
        return ResponseEntity.ok(UsuarioResponse.from(salvo));
    }

    @PutMapping("/ativar/{id}")
    public ResponseEntity<?> ativarUsuario(@PathVariable Long id) {
        Usuario salvo = usuarioService.ativarUsuario(id);
        sessaoService.revogarRefreshTokensDoUsuario(id);
        return ResponseEntity.ok(UsuarioResponse.from(salvo));
    }

    private boolean isSelf(Authentication auth, Usuario alvo) {
        return auth != null && alvo != null && alvo.getEmail() != null && alvo.getEmail().equals(auth.getName());
    }

    private String tokenDaRequisicao(String accessTokenCookie, HttpServletRequest request) {
        if (accessTokenCookie != null) return accessTokenCookie;
        String h = request.getHeader("Authorization");
        return h != null && h.startsWith("Bearer ") ? h.substring(7) : null;
    }
}
