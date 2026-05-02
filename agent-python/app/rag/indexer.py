from typing import Dict

from app.models import RagIndexJob


def plan_index_job(job: RagIndexJob) -> Dict[str, str]:
    return {
        "job_id": job.job_id,
        "document_id": job.document_id,
        "status": "accepted",
        "next_step": "parse_chunk_embed_upsert",
        "collection": job.milvus_collection,
    }
