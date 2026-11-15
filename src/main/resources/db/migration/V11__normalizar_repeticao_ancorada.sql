-- Normaliza os Segmentos de recorrência para a forma canônica do contrato de repetição ancorada na
-- data financeira (ADR 0015). Nenhuma data projetada muda.
--
-- Classificação de cada segmento:
--   MUDA_DATAS         mensal com início no dia 29 a 31 e (política PADRAO ou BYMONTHDAY presente): a
--                      expansão atual pula os meses sem esse dia, e o ajuste acrescentaria
--                      ocorrências. Também anual em 29/02 com política PADRAO.
--   NAO_REPRESENTAVEL  vários dias, dia diferente do início ou partes de RRULE fora do contrato.
--
-- Se existir algum segmento nessas classes, a migration falha antes de qualquer alteração e o deploy
-- para: esses segmentos exigem decisão manual.

DO $$
DECLARE
    bloqueados INTEGER;
BEGIN
    SELECT COUNT(*) INTO bloqueados
    FROM (
        WITH SEGMENTO AS (
            SELECT
                S.STR_RRULE,
                S.STR_POLITICA_DATA,
                S.DAT_DTSTART,
                SUBSTRING(S.STR_RRULE FROM 'FREQ=([A-Z]+)') AS FREQ,
                SUBSTRING(S.STR_RRULE FROM 'BYDAY=([^;]+)') AS BYDAY,
                SUBSTRING(S.STR_RRULE FROM 'BYMONTHDAY=([^;]+)') AS BYMONTHDAY,
                (ARRAY['MO', 'TU', 'WE', 'TH', 'FR', 'SA', 'SU'])[EXTRACT(ISODOW FROM S.DAT_DTSTART)::INT]
                    AS DIA_SEMANA_INICIO,
                EXTRACT(DAY FROM S.DAT_DTSTART)::INT AS DIA_MES_INICIO
            FROM TB005_SEGMENTO_RECORRENCIA S
        )
        SELECT
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
    ) CLASSIFICADO
    WHERE CLASSIFICACAO <> 'OK';

    IF bloqueados > 0 THEN
        RAISE EXCEPTION 'V11 bloqueada: % segmento(s) de recorrencia mudariam de datas ou nao tem representacao no novo contrato', bloqueados;
    END IF;
END $$;

-- Segmentos OK: o mensal perde BYMONTHDAY, que é igual ao dia do início, e o mensal e o anual passam
-- para AJUSTAR_ULTIMO_DIA_MES, que não muda datas porque o início cai em dia representável. Diários e
-- semanais usam PADRAO. Semanais mantêm o BYDAY, que é o dia do início, ou nenhum, que a RFC trata da
-- mesma forma.
UPDATE TB005_SEGMENTO_RECORRENCIA
SET STR_RRULE = CASE
        WHEN SUBSTRING(STR_RRULE FROM 'FREQ=([A-Z]+)') = 'MONTHLY'
            THEN REGEXP_REPLACE(STR_RRULE, ';BYMONTHDAY=[^;]+', '')
        ELSE STR_RRULE
    END,
    STR_POLITICA_DATA = CASE
        WHEN SUBSTRING(STR_RRULE FROM 'FREQ=([A-Z]+)') IN ('MONTHLY', 'YEARLY')
            THEN 'AJUSTAR_ULTIMO_DIA_MES'
        ELSE 'PADRAO'
    END;
