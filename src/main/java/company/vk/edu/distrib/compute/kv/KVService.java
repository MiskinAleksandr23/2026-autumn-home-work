package company.vk.edu.distrib.compute.kv;

import company.vk.edu.distrib.compute.HttpService;

/**
 * A storage with HTTP API.
 *
 * <p>
 * The following HTTP protocol is supported:
 * <ul>
 * <li>{@code GET /v0/status} -- returns {@code 200} or {@code 503}</li>
 * <li>{@code GET /v0/entity?id=<ID>} -- get data by {@code ID}. Returns {@code 200} and data
 *     or {@code 404} if not found.</li>
 * <li>{@code PUT /v0/entity?id=<ID>} -- upsert (create or replace) data by {@code ID}. Returns {@code 201}.</li>
 * <li>{@code DELETE /v0/entity?id=<ID>} -- remove data by {@code ID}. Returns {@code 202}.</li>
 * </ul>
 *
 * <p>
 * {@code ID} is a non-empty char sequence.
 *
 * <p>
 * In all the cases the storage may return:
 * <ul>
 * <li>{@code 4xx} for malformed requests</li>
 * <li>{@code 5xx} for internal errors</li>
 * </ul>
 *
 */
public interface KVService extends HttpService {
}
