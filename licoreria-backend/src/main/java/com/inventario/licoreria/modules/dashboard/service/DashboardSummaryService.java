package com.inventario.licoreria.modules.dashboard.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.inventario.licoreria.modules.administrative_costs.model.AdministrativeCostMovement;
import com.inventario.licoreria.modules.administrative_costs.repository.AdministrativeCostMovementRepository;
import com.inventario.licoreria.modules.dashboard.model.DashboardSummaryCache;
import com.inventario.licoreria.modules.dashboard.repository.DashboardSummaryCacheRepository;
import com.inventario.licoreria.modules.inventory.model.Transaction;
import com.inventario.licoreria.modules.inventory.repository.TransactionRepository;
import com.inventario.licoreria.modules.products.model.Product;
import com.inventario.licoreria.modules.products.model.ProductTag;
import com.inventario.licoreria.modules.store.model.Store;
import com.inventario.licoreria.modules.store.repository.StoreRepository;
import com.inventario.licoreria.modules.store.service.StoreService;
import com.inventario.licoreria.security.JwtUtil;

import jakarta.annotation.PostConstruct;

@Service
public class DashboardSummaryService {

    private static final Logger logger = LoggerFactory.getLogger(DashboardSummaryService.class);

    private final DashboardSummaryCacheRepository cacheRepository;
    private final TransactionRepository transactionRepository;
    private final AdministrativeCostMovementRepository movementRepository;
    private final StoreRepository storeRepository;
    private final StoreService storeService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final Set<Long> dirtyStores = ConcurrentHashMap.newKeySet();

    public DashboardSummaryService(
            DashboardSummaryCacheRepository cacheRepository,
            TransactionRepository transactionRepository,
            AdministrativeCostMovementRepository movementRepository,
            StoreRepository storeRepository,
            ObjectMapper objectMapper,
            StoreService storeService,
            JwtUtil jwtUtil) {
        this.cacheRepository = cacheRepository;
        this.transactionRepository = transactionRepository;
        this.movementRepository = movementRepository;
        this.storeRepository = storeRepository;
        this.objectMapper = objectMapper;
        this.storeService = storeService;
        this.jwtUtil = jwtUtil;
    }

    public void markStoreDirty(Long storeId) {
        if (storeId != null) {
            dirtyStores.add(storeId);
        }
    }

    @PostConstruct
    public void markExistingStoresDirty() {
        storeRepository.findAll().forEach(store -> markStoreDirty(store.getId()));
    }

    public JsonNode getSummary(@NonNull Long storeId) {
        return cacheRepository.findByStoreId(storeId)
                .map(cache -> readPayload(cache.getPayload()))
                .orElseGet(() -> objectMapper.createObjectNode()
                        .put("storeId", storeId)
                        .put("ready", false));
    }

    public void validateAccess(@NonNull Long storeId, String username, String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring("Bearer ".length());
            if (jwtUtil.isExternalAccess(token)) {
                if (!storeId.equals(jwtUtil.extractStoreId(token))) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a esta tienda");
                }
                return;
            }
        }
        storeService.validateUserAccess(storeService.findStoreEntity(storeId), username);
    }

    @Scheduled(
        fixedDelayString = "${dashboard.summary.refresh-ms:300000}",
        initialDelayString = "${dashboard.summary.initial-delay-ms:10000}")
    public void refreshDirtyStores() {
        if (dirtyStores.isEmpty()) {
            return;
        }
        Set<Long> pending = new HashSet<>(dirtyStores);
        logger.info("Actualizando resumen dashboard para {} tienda(s)", pending.size());
        pending.forEach(storeId -> {
            try {
                long startedAt = System.nanoTime();
                rebuildStore(storeId);
                dirtyStores.remove(storeId);
                logger.info("Resumen dashboard actualizado: storeId={}, duración={} ms", storeId,
                        (System.nanoTime() - startedAt) / 1_000_000);
            } catch (RuntimeException exception) {
                logger.warn("No se pudo actualizar el resumen dashboard de storeId={}; se reintentará en el próximo ciclo",
                        storeId, exception);
            }
        });
    }

    @Transactional
    @SuppressWarnings("null")
    public void rebuildStore(Long storeId) {
        Store store = storeRepository.findById(storeId).orElse(null);
        if (store == null) {
            dirtyStores.remove(storeId);
            return;
        }

        Map<String, DaySummary> days = new HashMap<>();
        List<Transaction> transactions = transactionRepository.findByStoreIdOrderByDateTimeDesc(storeId);
        for (Transaction transaction : transactions) {
            Product product = transaction.getProduct();
            if (Boolean.FALSE.equals(product.getIsActive())) {
                continue;
            }
            String date = transaction.getDateTime().toLocalDate().toString();
            DaySummary day = days.computeIfAbsent(date, ignored -> new DaySummary(date));
            day.addTransaction(transaction, product);
        }

        for (AdministrativeCostMovement movement : movementRepository.findByStoreId(storeId)) {
            if (movement.getDateTime() == null) {
                continue;
            }
            String date = movement.getDateTime().toLocalDate().toString();
            days.computeIfAbsent(date, ignored -> new DaySummary(date))
                    .adminCost = days.get(date).adminCost.add(value(movement.getAmountPaid()));
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("storeId", storeId);
        payload.put("ready", true);
        payload.put("generatedAt", LocalDateTime.now().toString());
        ArrayNode dayArray = payload.putArray("days");
        days.values().stream()
                .sorted(Comparator.comparing(day -> day.date))
                .forEach(day -> dayArray.add(day.toJson(objectMapper)));

        DashboardSummaryCache cache = cacheRepository.findByStoreId(storeId).orElseGet(DashboardSummaryCache::new);
        cache.setStoreId(storeId);
        cache.setPayload(payload.toString());
        cache.setUpdatedAt(LocalDateTime.now());
        cacheRepository.save(cache);
    }

    private JsonNode readPayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            return objectMapper.createObjectNode().put("ready", false);
        }
    }

    private static BigDecimal value(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    @SuppressWarnings("null")
    private static BigDecimal amount(Transaction transaction, Product product, boolean price) {
        BigDecimal unit = price ? product.getPrice() : product.getCost();
        Integer transactionQuantity = transaction.getQuantity();
        int quantity = transactionQuantity == null ? 0 : transactionQuantity.intValue();
        return value(unit).multiply(BigDecimal.valueOf(quantity));
    }

    private static final class DaySummary {
        private final String date;
        private int entries;
        private int salesUnits;
        private int salesCount;
        private int losses;
        private int movements;
        private BigDecimal entryCost = BigDecimal.ZERO;
        private BigDecimal salesRevenue = BigDecimal.ZERO;
        private BigDecimal salesCost = BigDecimal.ZERO;
        private BigDecimal adminCost = BigDecimal.ZERO;
        private final Map<Long, ProductSummary> products = new HashMap<>();
        private final Map<String, ProductSummary> categories = new HashMap<>();
        private final Map<String, Integer> payments = new HashMap<>();

        private DaySummary(String date) {
            this.date = date;
        }

        @SuppressWarnings("null")
        private void addTransaction(Transaction transaction, Product product) {
            Integer transactionQuantity = transaction.getQuantity();
            int quantity = transactionQuantity == null ? 0 : transactionQuantity.intValue();
            movements++;
            if ("ENTRADA".equalsIgnoreCase(transaction.getType())) {
                entries += quantity;
                if (!"AJUSTE".equalsIgnoreCase(transaction.getReason())) {
                    entryCost = entryCost.add(amount(transaction, product, false));
                }
            }
            if ("SALIDA".equalsIgnoreCase(transaction.getType())) {
                if ("VENTA".equalsIgnoreCase(transaction.getReason())) {
                    salesCount++;
                    salesUnits += quantity;
                    salesRevenue = salesRevenue.add(amount(transaction, product, true));
                    salesCost = salesCost.add(amount(transaction, product, false));
                } else if ("PERDIDA".equalsIgnoreCase(transaction.getReason())) {
                    losses += quantity;
                }
            }
            if ("SALIDA".equalsIgnoreCase(transaction.getType()) && "VENTA".equalsIgnoreCase(transaction.getReason())) {
                ProductSummary summary = products.computeIfAbsent(product.getId(), id -> new ProductSummary(id, product.getName()));
                summary.add(quantity, amount(transaction, product, true), amount(transaction, product, false));
                Set<String> names = new HashSet<>();
                if (product.getTags() != null) {
                    for (ProductTag productTag : product.getTags()) {
                        if (productTag.getTag() != null && productTag.getTag().getName() != null) {
                            names.add(productTag.getTag().getName());
                        }
                    }
                }
                if (names.isEmpty()) names.add("Sin etiqueta");
                names.forEach(name -> categories.computeIfAbsent(name, ignored -> new ProductSummary(null, name))
                        .add(quantity, amount(transaction, product, true), amount(transaction, product, false)));
                String payment = "Sin método";
                if (transaction.getPaymentMethod() != null) {
                    try {
                        payment = transaction.getPaymentMethod().getPaymentMethodConfig().getName();
                    } catch (RuntimeException ignored) {
                        // Preserve the transaction in the totals even if its payment relation is incomplete.
                    }
                }
                payments.merge(payment, quantity, (first, second) -> first + second);
            }
        }

        private ObjectNode toJson(ObjectMapper mapper) {
            ObjectNode json = mapper.createObjectNode();
            json.put("date", date);
            json.put("entries", entries);
            json.put("entryCost", entryCost);
            json.put("salesUnits", salesUnits);
            json.put("salesCount", salesCount);
            json.put("salesRevenue", salesRevenue);
            json.put("salesCost", salesCost);
            json.put("grossProfit", salesRevenue.subtract(salesCost));
            json.put("losses", losses);
            json.put("movements", movements);
            json.put("adminCost", adminCost);
            ArrayNode productArray = json.putArray("products");
            products.values().stream().sorted(Comparator.comparing(summary -> summary.name)).forEach(summary -> productArray.add(summary.toJson(mapper)));
            ArrayNode categoryArray = json.putArray("categories");
            categories.values().stream().sorted(Comparator.comparing(summary -> summary.name)).forEach(summary -> categoryArray.add(summary.toJson(mapper)));
            ObjectNode paymentObject = json.putObject("payments");
            payments.forEach(paymentObject::put);
            return json;
        }
    }

    private static final class ProductSummary {
        private final Long id;
        private final String name;
        private int units;
        private BigDecimal revenue = BigDecimal.ZERO;
        private BigDecimal cost = BigDecimal.ZERO;

        private ProductSummary(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        private void add(int quantity, BigDecimal revenue, BigDecimal cost) {
            units += quantity;
            this.revenue = this.revenue.add(revenue);
            this.cost = this.cost.add(cost);
        }

        private ObjectNode toJson(ObjectMapper mapper) {
            ObjectNode json = mapper.createObjectNode();
            if (id != null) json.put("productId", id);
            json.put("name", name);
            json.put("units", units);
            json.put("revenue", revenue);
            json.put("cost", cost);
            json.put("profit", revenue.subtract(cost));
            return json;
        }
    }
}
