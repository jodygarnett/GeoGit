/* Copyright (c) 2011-2012 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the LGPL 2.1 license, available at the root
 * application directory.
 */
package org.geogit.api;

import java.util.logging.Logger;

import org.geogit.api.RevObject.TYPE;
import org.geogit.api.config.RefIO;
import org.geogit.repository.Repository;
import org.geogit.repository.remote.payload.IPayload;
import org.geogit.storage.BlobWriter;
import org.geogit.storage.ObjectInserter;
import org.geogit.storage.WrappedSerialisingFactory;
import org.geotools.util.logging.Logging;

/**
 * A utility object to help insert new Commits, Trees, Blobs into the object database
 * @author jhudson
 */
public class PayloadUtil {

    Repository repository;

    protected final Logger LOGGER;

    public PayloadUtil(Repository repo) {
        super();
        this.repository = repo;
        LOGGER = Logging.getLogger(getClass());
    }

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public FetchResult applyPayloadTo(String branchName, IPayload payload) throws Exception {
        /*
         * The results of what has been added to the object database 
         */
    	FetchResult result = new FetchResult();
    	
        int commits = 0;
        int deltas = 0; /*fancy name for blobs*/
        
        /*
         * Used to write objects into the object database 
         */
        WrappedSerialisingFactory fact = WrappedSerialisingFactory.getInstance();
        ObjectInserter objectInserter = getRepository().newObjectInserter();

        if (payload == null) {
        	//nothing to do
        	return result;
        }
        
        /*
         * Update the local repos commits
         */
        for (RevCommit commit : payload.getCommitUpdates()) {
        	/*
        	 * ignore anything that is already in the repository - its likely this situation is
        	 * a commit that has been committed by this repository and pushed back to the server
        	 * - and so we already have it
        	 */
        	if (!getRepository().commitExists(commit.getId())) {
        		commits++;
            	ObjectId commitId = objectInserter.insert(fact.createCommitWriter(commit));
            	getRepository().getRefDatabase().put(new Ref(branchName, commitId, TYPE.COMMIT));
            	LOGGER.info("Adding commit: " + commit.toString() + " to " + branchName);
            	result.addCommit();
        	}
        }

        /*
         * Update the local repos trees
         */
        for (RevTree tree : payload.getTreeUpdates()) {
        	/*
        	 * ignore anything that is already in the repository - its likely this situation is
        	 * a commit that has been committed by this repository and pushed back to the server
        	 * - and so we already have it
        	 */
        	if (!getRepository().treeExists(tree.getId())){
        		ObjectId treeId = objectInserter.insert(fact.createRevTreeWriter(tree));
            	getRepository().getRefDatabase().put(new Ref(branchName, treeId, TYPE.TREE));
            	LOGGER.info("Adding tree: " + tree.toString());
            	result.addTree();
        	}
        }

        /*
         * Update the local repos blobs
         */
        for (RevBlob blob : payload.getBlobUpdates()) {
        	/*
        	 * ignore anything that is already in the repository - its likely this situation is
        	 * a commit that has been committed by this repository and pushed back to the server
        	 * - and so we already have it
        	 */
        	if (!getRepository().blobExists(blob.getId())){
        		deltas++;
            	ObjectId blobId = objectInserter.insert(new BlobWriter((byte[]) blob.getParsed()));
            	getRepository().getRefDatabase().put(new Ref(branchName, blobId, TYPE.BLOB));
            	result.addBlob();
        	}
        }

        LOGGER.info("Remote: counted " + commits + " commits (" + deltas + " deltas), done.");
        LOGGER.info("Added " + commits + " new commits added to repository");
        LOGGER.info("Added " + deltas + " new deltas added to repository");

        /*
         * Update the local repos branch refs for the remote so the branch head is 
         * pointing at the correct commit
         *  
         * for (RevTag tag: payload.getTagUpdates()) { 
         * 		deltas++; 
         * 		ObjectId tagId = objectInserter.insert(new RevTagWriter(tag)); 
         * 		Ref ref = new Ref(remote.getName(), tagId, TYPE.TAG); 
         * 		getRepository().getRefDatabase().put(ref); 
         * }
         */
        for (String name : payload.getBranchUpdates().keySet()) {
            Ref ref = payload.getBranchUpdates().get(name);
            /*
             * Now we must write out all the remote heads - so we can keep track of them for fetches
             */
            Ref remoteRef = new Ref(Ref.REMOTES_PREFIX + branchName + "/" + Ref.MASTER,
                    ref.getObjectId(), TYPE.REMOTE);
            Ref oldRef = getRepository().getRef(remoteRef.getName());

            if (oldRef != null && !oldRef.equals(remoteRef)) {
                LOGGER.info("  " + remoteRef.getObjectId().printSmallId() + " " + name + " -> "
                        + remoteRef.getName());
                getRepository().updateRef(remoteRef);
                RefIO.writeRemoteRefs(getRepository().getRepositoryHome(), branchName, name,
                        ref.getObjectId());
            }
            result.addBranch();
        }
        return result;
    }
}
