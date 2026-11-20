-- Diagnóstico somente leitura dos Segmentos de recorrência existentes.
-- Classifica cada segmento conforme o novo contrato de repetição (spec.md, "Segmentos existentes")
-- e devolve apenas contagens, sem dados financeiros.
--
--   OK                 representável no novo contrato com exatamente as mesmas datas
--   MUDA_DATAS         representável, mas o ajuste para o último dia do mês acrescentaria ocorrências:
--                      mensal com início no dia 29 a 31 e (política PADRAO ou BYMONTHDAY presente),
--                      ou anual em 29/02 com política PADRAO
--   NAO_REPRESENTAVEL  usa recursos de RRULE que o novo contrato não expressa
--
-- Funciona em qualquer schema a partir da V6. A política de data é derivada do tipo de grupo,
-- como a V8 faz ao criar a coluna. Por isso não depende de STR_POLITICA_DATA. Em bases que já
-- passaram da V8, uma recorrência comum gravada com AJUSTAR_ULTIMO_DIA_MES pode aparecer como
-- MUDA_DATAS quando o início é do dia 29 a 31 e não há BYMONTHDAY. Esse é um falso positivo
-- conservador, e a migration V11 usa a coluna real.
WITH SEGMENTO AS (
    SELECT
        S.ID_REGISTRO,
        G.STR_TIPO AS TIPO_GRUPO,
        S.STR_STATUS,
        CASE WHEN G.STR_TIPO = 'PARCELAMENTO' THEN 'AJUSTAR_ULTIMO_DIA_MES' ELSE 'PADRAO' END
            AS STR_POLITICA_DATA,
        S.STR_RRULE,
        S.DAT_DTSTART,
        SUBSTRING(S.STR_RRULE FROM 'FREQ=([A-Z]+)') AS FREQ,
        SUBSTRING(S.STR_RRULE FROM 'BYDAY=([^;]+)') AS BYDAY,
        SUBSTRING(S.STR_RRULE FROM 'BYMONTHDAY=([^;]+)') AS BYMONTHDAY,
        (ARRAY['MO', 'TU', 'WE', 'TH', 'FR', 'SA', 'SU'])[EXTRACT(ISODOW FROM S.DAT_DTSTART)::INT]
            AS DIA_SEMANA_INICIO,
        EXTRACT(DAY FROM S.DAT_DTSTART)::INT AS DIA_MES_INICIO
    FROM TB005_SEGMENTO_RECORRENCIA S
    JOIN TB004_GRUPO_RECORRENCIA G
      ON G.ID_REGISTRO = S.ID_GRUPO_RECORRENCIA
     AND G.ID_USUARIO = S.ID_USUARIO
),
CLASSIFICADO AS (
    SELECT
        SEGMENTO.*,
        CASE
            WHEN STR_RRULE ~ '(BYSETPOS|BYMONTH=|BYYEARDAY|BYWEEKNO|BYHOUR|BYMINUTE|BYSECOND)'
                THEN 'NAO_REPRESENTAVEL'
            WHEN FREQ IN ('DAILY', 'YEARLY') AND (BYDAY IS NOT NULL OR BYMONTHDAY IS NOT NULL)
                THEN 'NAO_REPRESENTAVEL'
            WHEN FREQ = 'WEEKLY'
                 AND (BYMONTHDAY IS NOT NULL OR COALESCE(BYDAY, DIA_SEMANA_INICIO) <> DIA_SEMANA_INICIO)
                THEN 'NAO_REPRESENTAVEL'
            WHEN FREQ = 'MONTHLY'
                 AND (BYDAY IS NOT NULL OR COALESCE(BYMONTHDAY, DIA_MES_INICIO::TEXT) <> DIA_MES_INICIO::TEXT)
                THEN 'NAO_REPRESENTAVEL'
            WHEN FREQ = 'MONTHLY' AND DIA_MES_INICIO >= 29
                 AND (STR_POLITICA_DATA = 'PADRAO' OR BYMONTHDAY IS NOT NULL)
                THEN 'MUDA_DATAS'
            WHEN STR_POLITICA_DATA = 'PADRAO' AND FREQ = 'YEARLY'
                 AND TO_CHAR(DAT_DTSTART, 'MM-DD') = '02-29'
                THEN 'MUDA_DATAS'
            ELSE 'OK'
        END AS CLASSIFICACAO
    FROM SEGMENTO
)
SELECT CLASSIFICACAO, FREQ, TIPO_GRUPO, STR_POLITICA_DATA, STR_STATUS, COUNT(*) AS QUANTIDADE
FROM CLASSIFICADO
GROUP BY CLASSIFICACAO, FREQ, TIPO_GRUPO, STR_POLITICA_DATA, STR_STATUS
ORDER BY CLASSIFICACAO, FREQ, TIPO_GRUPO, STR_POLITICA_DATA, STR_STATUS;
