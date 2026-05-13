import matplotlib.pyplot as plt
import pandas as pd

singleLink = pd.read_csv('linkfailure.csv', delimiter=' ')
partition = pd.read_csv('partition.csv', delimiter=' ')

print(singleLink.head())
for i in range(3): print()
print(partition.head())

plt.plot(singleLink['num_nodes'], singleLink['dvr_avg_msgs'])
plt.plot(singleLink['num_nodes'], singleLink['bsdvr_avg_msgs'])
plt.xlabel('Number of Nodes')
plt.ylabel('Average Messages')
plt.title('Single Link Failure - Average Messages')
plt.show()

plt.plot(partition['num_nodes'], partition['dvr_avg_msgs'])
plt.plot(partition['num_nodes'], partition['bsdvr_avg_msgs'])
plt.xlabel('Number of Nodes')
plt.ylabel('Average Messages')
plt.title('Partitioned Network - Average Messages')
plt.show()