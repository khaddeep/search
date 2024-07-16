import json

from pymilvus import (
    FieldSchema, CollectionSchema, DataType, utility,
    Collection, AnnSearchRequest, RRFRanker, connections, WeightedRanker
)

from pymilvus.model.sparse import BM25EmbeddingFunction
from pymilvus.model.sparse.bm25.tokenizers import build_default_analyzer

analyzer = build_default_analyzer(language="en")
bm25_ef = BM25EmbeddingFunction(analyzer)

from sentence_transformers import SentenceTransformer

model = SentenceTransformer('intfloat/e5-small-v2')



def test_hybrid_insert():
    connections.connect("default", uri="./milvus_demo.db")

    fields = [
        FieldSchema(name="pk", dtype=DataType.VARCHAR,
                    is_primary=True, auto_id=True, max_length=100),
        FieldSchema(name="text", dtype=DataType.VARCHAR, max_length=512),
        FieldSchema(name="sparse_vector", dtype=DataType.SPARSE_FLOAT_VECTOR),
        FieldSchema(name="dense_vector", dtype=DataType.FLOAT_VECTOR,
                    dim=384),
        FieldSchema(name="chunk_metadata", dtype=DataType.JSON)
    ]

    schema = CollectionSchema(fields, "")
    col_name = 'hybrid_test'
    col = Collection(col_name, schema, consistency_level="Strong")
    docs = [
        "Artificial intelligence was founded as an academic discipline in 1956.",
        "Alan Turing was the first person to conduct substantial research in AI.",
        "Born in Maida Vale, London, Turing was raised in southern England.",
    ]
    extracted_entities = [{"date": "2020-11-12"}, {"date": "2021-12-12", "FANUMBER": "123422"}, {}]
    sparse_index = {"index_type": "SPARSE_INVERTED_INDEX", "metric_type": "IP"}
    col.create_index("sparse_vector", sparse_index)
    dense_index = {"index_type": "FLAT", "metric_type": "L2"}
    col.create_index("dense_vector", dense_index)
    col.load()
    bm25_ef.fit(docs)
    docs_embeddings = bm25_ef.encode_documents(docs)
    dense_embeddings = model.encode(docs)
    print(dense_embeddings)
    entities = [docs, docs_embeddings, dense_embeddings, extracted_entities]
    col.insert(entities)


def test_hybrid_search():
    connections.connect("default", uri="./milvus_demo.db")

    fields = [
        FieldSchema(name="pk", dtype=DataType.VARCHAR,
                    is_primary=True, auto_id=True, max_length=100),
        FieldSchema(name="text", dtype=DataType.VARCHAR, max_length=512),
        FieldSchema(name="sparse_vector", dtype=DataType.SPARSE_FLOAT_VECTOR),
        FieldSchema(name="dense_vector", dtype=DataType.FLOAT_VECTOR,
                    dim=384),
        FieldSchema(name="chunk_metadata", dtype=DataType.JSON)
    ]

    schema = CollectionSchema(fields, "")
    col_name = 'hybrid_test'
    col = Collection(col_name, schema, consistency_level="Strong")
    query = "Who started AI research?"
    # facets = "FANUMBER == 123422".strip().split()
    facets = "date > 2010-11-12 && date < 2023-11-12".split()
    # facets = ""
    k = 3
    query_embeddings = model.encode([query])
    if len(facets) == 3:
        filter_entities = f'chunk_metadata["{facets[0]}"] {facets[1]} "{facets[2]}"'
    elif len(facets) == 7:
        filter_entities = f'chunk_metadata["{facets[0]}"] {facets[1]} "{facets[2]}" {facets[3]} chunk_metadata["{facets[4]}"] {facets[5]} "{facets[6]}"'
    else:
        filter_entities = ""
    sparse_search_params = {"metric_type": "IP"}
    sparse_req = AnnSearchRequest(bm25_ef.encode_queries([query]), "sparse_vector", sparse_search_params, limit=k,
                                  expr=filter_entities)
    dense_search_params = {"metric_type": "L2"}
    dense_req = AnnSearchRequest(query_embeddings, "dense_vector", dense_search_params, limit=k, expr=filter_entities)

    res = col.hybrid_search([sparse_req, dense_req], rerank=RRFRanker(), limit=k,
                            output_fields=['text', 'chunk_metadata'])
    print(res[0])


if __name__ == '__main__':
    # test_hybrid_insert()
    test_hybrid_search()
