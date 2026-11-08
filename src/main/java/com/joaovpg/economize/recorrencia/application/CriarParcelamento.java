package com.joaovpg.economize.recorrencia.application;

import com.joaovpg.economize.categoria.Categoria;
import com.joaovpg.economize.categoria.CategoriaRepository;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import com.joaovpg.economize.recorrencia.GrupoRecorrencia;
import com.joaovpg.economize.recorrencia.GrupoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.SegmentoRecorrencia;
import com.joaovpg.economize.recorrencia.SegmentoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.enums.FrequenciaRecorrencia;
import com.joaovpg.economize.recorrencia.enums.PoliticaDataOcorrencia;
import com.joaovpg.economize.recorrencia.enums.StatusRecorrencia;
import com.joaovpg.economize.recorrencia.enums.TipoGrupoRecorrencia;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import com.joaovpg.economize.transacao.TipoTransacao;
import com.joaovpg.economize.usuario.UsuarioRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class CriarParcelamento {
  private final UsuarioRepository usuarioRepository;
  private final ContaFinanceiraRepository contaRepository;
  private final CategoriaRepository categoriaRepository;
  private final GrupoRecorrenciaRepository grupoRepository;
  private final SegmentoRecorrenciaRepository segmentoRepository;

  public CriarParcelamento(
      UsuarioRepository usuarioRepository,
      ContaFinanceiraRepository contaRepository,
      CategoriaRepository categoriaRepository,
      GrupoRecorrenciaRepository grupoRepository,
      SegmentoRecorrenciaRepository segmentoRepository) {
    this.usuarioRepository = usuarioRepository;
    this.contaRepository = contaRepository;
    this.categoriaRepository = categoriaRepository;
    this.grupoRepository = grupoRepository;
    this.segmentoRepository = segmentoRepository;
  }

  @Transactional
  public Resultado executar(Comando comando) {
    RecorrenciaValidacao.dadosFinanceiros(
        comando.tipo(),
        comando.descricao(),
        comando.observacoes(),
        comando.valorPorParcela(),
        comando.inicio());
    if (comando.numeroPrimeiraParcela() == null || comando.numeroPrimeiraParcela() < 1) {
      throw new RegraNegocioException(
          "NUMERO_PRIMEIRA_PARCELA_INVALIDO", "O numero da primeira parcela deve ser positivo");
    }
    if (comando.quantidadeTotalOriginal() == null
        || comando.quantidadeTotalOriginal() < comando.numeroPrimeiraParcela()) {
      throw new RegraNegocioException(
          "QUANTIDADE_PARCELAS_INVALIDA",
          "A quantidade total deve ser maior ou igual a primeira parcela");
    }
    var usuario = RecorrenciaValidacao.usuario(usuarioRepository, comando.usuarioId());
    var conta =
        RecorrenciaValidacao.conta(
            contaRepository, comando.contaId(), comando.usuarioId(), comando.inicio());
    Categoria categoria =
        RecorrenciaValidacao.categoria(
            categoriaRepository, comando.categoriaId(), comando.usuarioId());
    var quantidadeRestante =
        comando.quantidadeTotalOriginal() - comando.numeroPrimeiraParcela() + 1;
    var frequencia = comando.frequencia();
    var regra =
        RecorrenciaValidacao.regra(
            comando.inicio(),
            frequencia,
            comando.intervalo(),
            frequencia == FrequenciaRecorrencia.WEEKLY
                ? Set.of(comando.inicio().getDayOfWeek())
                : Set.of(),
            Set.of(),
            quantidadeRestante,
            null);

    var grupo = new GrupoRecorrencia();
    grupo.setUsuario(usuario);
    grupo.setDescricao(comando.descricao().strip());
    grupo.setTipo(TipoGrupoRecorrencia.PARCELAMENTO);
    grupo.setStatus(StatusRecorrencia.ATIVO);
    grupoRepository.persist(grupo);

    var segmento = new SegmentoRecorrencia();
    segmento.setUsuario(usuario);
    segmento.setGrupo(grupo);
    segmento.setConta(conta);
    segmento.setCategoria(categoria);
    segmento.setTipo(comando.tipo());
    segmento.setDescricao(comando.descricao().strip());
    segmento.setObservacoes(comando.observacoes() == null ? null : comando.observacoes().strip());
    segmento.setValor(comando.valorPorParcela());
    segmento.setInicio(comando.inicio());
    segmento.setRrule(regra.rrule());
    segmento.setTotalOcorrencias(quantidadeRestante);
    segmento.setNumeroPrimeiraParcela(comando.numeroPrimeiraParcela());
    segmento.setQuantidadeTotalOriginal(comando.quantidadeTotalOriginal());
    segmento.setPoliticaData(PoliticaDataOcorrencia.AJUSTAR_ULTIMO_DIA_MES);
    segmento.setStatus(StatusRecorrencia.ATIVO);
    segmentoRepository.persist(segmento);

    return Resultado.de(segmento);
  }

  public record Comando(
      UUID usuarioId,
      UUID contaId,
      UUID categoriaId,
      TipoTransacao tipo,
      String descricao,
      String observacoes,
      BigDecimal valorPorParcela,
      LocalDate inicio,
      FrequenciaRecorrencia frequencia,
      Integer intervalo,
      Integer numeroPrimeiraParcela,
      Integer quantidadeTotalOriginal) {}

  public record Resultado(
      UUID grupoId,
      UUID segmentoId,
      TipoGrupoRecorrencia tipoGrupo,
      StatusRecorrencia status,
      TipoTransacao tipo,
      String descricao,
      String observacoes,
      BigDecimal valorPorParcela,
      LocalDate inicio,
      LocalDate fim,
      String rrule,
      Integer totalOcorrencias,
      Integer numeroPrimeiraParcela,
      Integer quantidadeTotalOriginal,
      PoliticaDataOcorrencia politicaDataOcorrencia) {
    static Resultado de(SegmentoRecorrencia segmento) {
      return new Resultado(
          segmento.getGrupo().getId(),
          segmento.getId(),
          segmento.getGrupo().getTipo(),
          segmento.getStatus(),
          segmento.getTipo(),
          segmento.getDescricao(),
          segmento.getObservacoes(),
          segmento.getValor(),
          segmento.getInicio(),
          segmento.getFim(),
          segmento.getRrule(),
          segmento.getTotalOcorrencias(),
          segmento.getNumeroPrimeiraParcela(),
          segmento.getQuantidadeTotalOriginal(),
          PoliticaDataOcorrencia.AJUSTAR_ULTIMO_DIA_MES);
    }
  }
}
